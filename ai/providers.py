"""
Multi-provider LLM 추상화.

각 provider 는 동일한 generate(prompt, temperature, json_mode) 시그니처.
환경변수로 활성화되며, 키가 없으면 그 provider 는 skip.

지원:
- openai_gpt4o_mini  (OPENAI_API_KEY)
- openai_gpt4o       (OPENAI_API_KEY)
- anthropic_claude35_sonnet  (ANTHROPIC_API_KEY)
- anthropic_claude3_haiku    (ANTHROPIC_API_KEY)
- google_gemini_25_flash     (GEMINI_API_KEY)
- google_gemini_25_flash_lite (GEMINI_API_KEY)

용도 (purpose) 별 우선순위:
- classify_emotion: openai_gpt4o_mini → anthropic_claude3_haiku → google_gemini_25_flash_lite
- diary_meditation: anthropic_claude35_sonnet → openai_gpt4o
- game_branch:      openai_gpt4o_mini → anthropic_claude3_haiku
- polish_psalm:     anthropic_claude35_sonnet → openai_gpt4o
- weekly_report:    anthropic_claude35_sonnet → openai_gpt4o
"""
import os
from typing import Optional

# Lazy import — 의존성이 없거나 키가 없는 provider 는 비활성화.
try:
    from openai import OpenAI
    _OPENAI_AVAILABLE = True
except ImportError:
    _OPENAI_AVAILABLE = False

try:
    from anthropic import Anthropic
    _ANTHROPIC_AVAILABLE = True
except ImportError:
    _ANTHROPIC_AVAILABLE = False

try:
    from google import genai as _g
    from google.genai import types as _g_types
    from google.genai.errors import ServerError as _GServerError
    _GEMINI_AVAILABLE = True
except ImportError:
    _GEMINI_AVAILABLE = False


class ProviderError(RuntimeError):
    """Provider 호출 실패 — fallback 으로 넘어가야 함."""


class _OpenAIProvider:
    def __init__(self, model: str):
        self.name = f"openai_{model.replace('-', '_').replace('.', '_')}"
        self.model = model
        self.client: Optional["OpenAI"] = None
        if _OPENAI_AVAILABLE and os.environ.get("OPENAI_API_KEY"):
            self.client = OpenAI()

    @property
    def available(self) -> bool:
        return self.client is not None

    def generate(self, prompt: str, temperature: float = 0.5, json_mode: bool = False) -> dict:
        if not self.available:
            raise ProviderError(f"{self.name} not configured")
        try:
            kwargs = {
                "model": self.model,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": temperature,
            }
            if json_mode:
                kwargs["response_format"] = {"type": "json_object"}
            r = self.client.chat.completions.create(**kwargs)
            return {
                "text": r.choices[0].message.content or "",
                "provider": "openai",
                "model": self.model,
                "promptTokens": r.usage.prompt_tokens if r.usage else None,
                "completionTokens": r.usage.completion_tokens if r.usage else None,
            }
        except Exception as e:
            raise ProviderError(f"openai {self.model}: {e}") from e


class _AnthropicProvider:
    def __init__(self, model: str):
        self.name = f"anthropic_{model.replace('-', '_').replace('.', '_')}"
        self.model = model
        self.client: Optional["Anthropic"] = None
        if _ANTHROPIC_AVAILABLE and os.environ.get("ANTHROPIC_API_KEY"):
            self.client = Anthropic()

    @property
    def available(self) -> bool:
        return self.client is not None

    def generate(self, prompt: str, temperature: float = 0.5, json_mode: bool = False) -> dict:
        if not self.available:
            raise ProviderError(f"{self.name} not configured")
        try:
            # Claude 는 native JSON mode 가 없으므로 prompt 에서 강제.
            sys_prompt = "Respond with valid JSON only." if json_mode else ""
            r = self.client.messages.create(
                model=self.model,
                max_tokens=1024,
                temperature=temperature,
                system=sys_prompt,
                messages=[{"role": "user", "content": prompt}],
            )
            text = ""
            for block in r.content:
                if hasattr(block, "text"):
                    text += block.text
            return {
                "text": text.strip(),
                "provider": "anthropic",
                "model": self.model,
                "promptTokens": r.usage.input_tokens,
                "completionTokens": r.usage.output_tokens,
            }
        except Exception as e:
            raise ProviderError(f"anthropic {self.model}: {e}") from e


class _GeminiProvider:
    def __init__(self, model: str):
        self.name = f"google_{model.replace('-', '_').replace('.', '_')}"
        self.model = model
        self.client = None
        if _GEMINI_AVAILABLE and os.environ.get("GEMINI_API_KEY"):
            self.client = _g.Client(api_key=os.environ["GEMINI_API_KEY"])

    @property
    def available(self) -> bool:
        return self.client is not None

    def generate(self, prompt: str, temperature: float = 0.5, json_mode: bool = False) -> dict:
        if not self.available:
            raise ProviderError(f"{self.name} not configured")
        try:
            config = _g_types.GenerateContentConfig(
                temperature=temperature,
                response_mime_type="application/json" if json_mode else "text/plain",
            )
            r = self.client.models.generate_content(
                model=self.model, contents=prompt, config=config)
            return {
                "text": (r.text or "").strip(),
                "provider": "google",
                "model": self.model,
                "promptTokens": None,
                "completionTokens": None,
            }
        except Exception as e:
            raise ProviderError(f"gemini {self.model}: {e}") from e


# 단일 인스턴스로 캐싱.
_REGISTRY = {
    "openai_gpt4o_mini": _OpenAIProvider("gpt-4o-mini"),
    "openai_gpt4o": _OpenAIProvider("gpt-4o"),
    "anthropic_claude35_sonnet": _AnthropicProvider("claude-3-5-sonnet-20241022"),
    "anthropic_claude3_haiku": _AnthropicProvider("claude-3-haiku-20240307"),
    "google_gemini_25_flash": _GeminiProvider("gemini-2.5-flash"),
    "google_gemini_25_flash_lite": _GeminiProvider("gemini-2.5-flash-lite"),
    "google_gemini_2_flash": _GeminiProvider("gemini-2.0-flash-001"),
}

# 용도별 우선순위 — application.yml 의 ai.provider-priority 와 동일.
PROVIDER_PRIORITY: dict[str, list[str]] = {
    "classify_emotion": ["openai_gpt4o_mini", "anthropic_claude3_haiku", "google_gemini_25_flash_lite"],
    "diary_meditation": ["anthropic_claude35_sonnet", "openai_gpt4o", "google_gemini_25_flash"],
    "game_branch":      ["openai_gpt4o_mini", "anthropic_claude3_haiku", "google_gemini_25_flash"],
    "game_reaction":    ["openai_gpt4o_mini", "anthropic_claude3_haiku", "google_gemini_25_flash"],
    "polish_psalm":     ["anthropic_claude35_sonnet", "openai_gpt4o", "google_gemini_25_flash"],
    "weekly_report":    ["anthropic_claude35_sonnet", "openai_gpt4o", "google_gemini_25_flash"],
    "npc_dialogue":     ["openai_gpt4o_mini", "anthropic_claude3_haiku", "google_gemini_25_flash"],
}

# 기본 fallback — purpose 가 PROVIDER_PRIORITY 에 없을 때.
DEFAULT_PRIORITY = ["google_gemini_25_flash", "openai_gpt4o_mini", "anthropic_claude3_haiku"]


def generate(purpose: str, prompt: str, temperature: float = 0.5, json_mode: bool = False) -> dict:
    """우선순위 순서로 provider 호출. 첫 성공 응답 반환. 모두 실패 시 ProviderError 마지막 케이스 raise."""
    priority = PROVIDER_PRIORITY.get(purpose, DEFAULT_PRIORITY)
    last_err: Optional[Exception] = None
    for pname in priority:
        provider = _REGISTRY.get(pname)
        if provider is None or not provider.available:
            continue
        try:
            return provider.generate(prompt, temperature=temperature, json_mode=json_mode)
        except ProviderError as e:
            last_err = e
            continue
    if last_err:
        raise last_err
    raise ProviderError(f"no provider available for purpose={purpose}")


def status() -> dict:
    return {
        "available": {name: p.available for name, p in _REGISTRY.items()},
        "priority": PROVIDER_PRIORITY,
    }
