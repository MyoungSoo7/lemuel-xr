"""
Lemuel XR — AI orchestration service.

backend (Spring Boot) 가 호출하는 LLM 사이드카.
용도 (purpose) 별 multi-provider 라우팅 — providers.py 참조.

엔드포인트:
  GET  /healthz
  GET  /providers          provider 별 활성화 상태 + 우선순위
  POST /ai/generate        { purpose, promptKey, variables } → { text, provider, model, ... }
                           Spring AiSidecarClient.generate 가 호출.
  POST /classify-emotion   { text } → { emotion, confidence }  (legacy)
  POST /joseph-monologue   { decision } → { text }              (legacy)
  POST /joseph-reunion     { decision, distribution_pattern } → { text }  (legacy)
"""
import json
import os
from typing import Any, Literal, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

import providers

# OpenTelemetry — Tempo OTLP exporter (best-effort import)
try:
    import tracing as _tracing
    _TRACING_AVAILABLE = True
except ImportError:
    _TRACING_AVAILABLE = False

# Legacy Gemini 직접 호출 (backward compat)
try:
    from google import genai
    from google.genai import types
    from google.genai.errors import ServerError
    _LEGACY_AVAILABLE = True
except ImportError:
    _LEGACY_AVAILABLE = False

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
LEGACY_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
LEGACY_FALLBACKS = os.environ.get(
    "GEMINI_FALLBACKS", "gemini-2.5-flash-lite,gemini-2.0-flash-001"
).split(",")

_legacy_client = (
    genai.Client(api_key=GEMINI_API_KEY)
    if _LEGACY_AVAILABLE and GEMINI_API_KEY
    else None
)
app = FastAPI(title="lemuel-xr-ai")
if _TRACING_AVAILABLE:
    try:
        _tracing.setup(app)
    except Exception:
        # OTel 설정 실패는 backend 에 영향 X — graceful skip
        pass

Emotion = Literal["ANXIOUS", "SAD", "ANGRY", "CONFUSED", "LONELY", "EXHAUSTED", "GRATEFUL"]


# ===================================================================
# Prompt templates (promptKey → prompt builder)
# ===================================================================
def _emotion_prompt(text: str) -> str:
    return f"""너는 한국어 감정 분류기다. 다음 사용자 텍스트를 7개 감정 중 하나로 분류하라.

가능한 감정:
ANXIOUS    - 불안, 두려움, 걱정
SAD        - 슬픔, 우울, 상실
ANGRY      - 분노, 짜증, 답답함
CONFUSED   - 혼란, 막막함
LONELY     - 외로움, 고립감
EXHAUSTED  - 지침, 번아웃, 무기력
GRATEFUL   - 감사, 평안

응답은 JSON 한 줄: {{"emotion": "ANXIOUS", "confidence": 0.85}}

사용자 텍스트: {text}
"""


def _build_prompt(prompt_key: str, variables: dict[str, Any]) -> str:
    """promptKey → prompt 본문 생성. 새 promptKey 는 여기 추가."""
    v = variables or {}
    if prompt_key == "emotion.classify":
        return _emotion_prompt(v["text"])
    if prompt_key == "joseph.s2.monologue":
        pct = {"save_20": "20", "save_33": "33", "save_50": "50"}.get(v["decision"], "33")
        return (
            "당신은 청년 요셉입니다. 7년 풍년 동안 전체 곡식의 "
            f"{pct}% 를 저장하기로 결정했습니다.\n"
            "내면 독백을 한국어 2~3 문장으로 작성하세요. "
            "백성 책임감과 두려움이 섞여 있어야 합니다."
        )
    if prompt_key == "joseph.s4.reunion":
        priority = {
            "farmer_first": "이집트 농민",
            "immigrant_first": "이주민 가족",
            "merchant_first": "무역 상인",
        }.get(v.get("distribution_pattern", "farmer_first"), "이집트 농민")
        action = {
            "reveal": "정체를 즉시 밝히는 것",
            "test": "잠시 시험하는 것 (베냐민 데려오라)",
            "silent": "침묵하는 것",
        }.get(v.get("decision", "reveal"), "정체를 즉시 밝히는 것")
        return (
            "당신은 청년 요셉, 이집트 총리입니다.\n"
            f"7년 흉년에 {priority} 에게 곡식을 우선 분배했습니다.\n"
            f"형제들이 곡식을 구하러 왔습니다. 당신은 {action} 을 선택했습니다.\n"
            "이 상황에서 요셉이 형제들에게 하는 첫 마디를 한국어 한 줄로 작성하세요. "
            "분배 패턴이 톤에 영향을 줍니다."
        )
    if prompt_key == "moses.s3.outcome":
        pattern = v.get("cards_pattern", "mixed")
        return (
            "당신은 광야의 모세입니다. 떨기나무 앞에서 다섯 가지 변명을 "
            f"이렇게 처리했습니다: {pattern}. "
            "이 결정 이후 모세의 한 줄 독백을 한국어로 작성하세요. "
            "결단·고백·동행 인식 중 패턴에 맞게."
        )
    if prompt_key == "david.s4.last_stone":
        stone = v.get("last_stone", "trust")
        return (
            "당신은 소년 다윗입니다. 시냇가에서 다섯 돌을 골라 주머니에 넣었고 "
            f"마지막으로 가져간 감정은 {stone} 입니다.\n"
            "골리앗 앞으로 나서기 직전 다윗의 내면 한 줄을 한국어로 작성하세요."
        )
    if prompt_key == "diary.meditation":
        diary = v.get("diary", "")
        emotion = v.get("emotion", "")
        return (
            "다음 일기에 시편 톤의 묵상 응답을 한국어 3~4 문장으로 작성하세요. "
            "안티-가스라이팅, AI 보조 임을 인지하고 자기치료를 대체하지 않는 톤.\n\n"
            f"감정: {emotion}\n일기:\n{diary}"
        )
    kv = "\n".join(f"{k}: {val}" for k, val in v.items())
    return f"promptKey={prompt_key}\n{kv}"


# ===================================================================
# /ai/generate — multi-provider 엔드포인트
# ===================================================================
class GenerateRequest(BaseModel):
    purpose: str
    promptKey: str
    variables: Optional[dict[str, Any]] = None
    temperature: Optional[float] = 0.5
    jsonMode: Optional[bool] = False


class GenerateResponse(BaseModel):
    text: str
    provider: str
    model: str
    promptTokens: Optional[int] = None
    completionTokens: Optional[int] = None
    cached: bool = False


@app.get("/healthz")
def healthz():
    return {"status": "ok", "legacy_model": LEGACY_MODEL, "legacy_configured": _legacy_client is not None}


@app.get("/providers")
def provider_status():
    return providers.status()


@app.post("/ai/generate", response_model=GenerateResponse)
def ai_generate(req: GenerateRequest) -> GenerateResponse:
    prompt = _build_prompt(req.promptKey, req.variables or {})
    try:
        r = providers.generate(
            purpose=req.purpose,
            prompt=prompt,
            temperature=req.temperature or 0.5,
            json_mode=req.jsonMode or False,
        )
        return GenerateResponse(
            text=r["text"],
            provider=r.get("provider", "unknown"),
            model=r.get("model", "unknown"),
            promptTokens=r.get("promptTokens"),
            completionTokens=r.get("completionTokens"),
        )
    except providers.ProviderError as e:
        raise HTTPException(status_code=502, detail=str(e))


# ===================================================================
# Legacy endpoints — backward compat (deprecated, /ai/generate 사용 권장)
# ===================================================================
def _legacy_generate(prompt: str, *, temperature: float = 0.5, json_mode: bool = False) -> str:
    if _legacy_client is None:
        return ""
    config = types.GenerateContentConfig(
        temperature=temperature,
        response_mime_type="application/json" if json_mode else "text/plain",
    )
    models_to_try = [LEGACY_MODEL] + [
        m.strip() for m in LEGACY_FALLBACKS if m.strip() and m.strip() != LEGACY_MODEL
    ]
    last_err: Exception | None = None
    for m in models_to_try:
        try:
            rsp = _legacy_client.models.generate_content(model=m, contents=prompt, config=config)
            return (rsp.text or "").strip()
        except ServerError as e:
            last_err = e
            continue
    if last_err:
        raise last_err
    return ""


class EmotionRequest(BaseModel):
    text: str


class EmotionResponse(BaseModel):
    emotion: Emotion
    confidence: float


@app.post("/classify-emotion", response_model=EmotionResponse)
def classify_emotion(req: EmotionRequest) -> EmotionResponse:
    try:
        r = providers.generate(
            purpose="classify_emotion",
            prompt=_emotion_prompt(req.text),
            temperature=0.0,
            json_mode=True,
        )
        data = json.loads(r["text"])
        return EmotionResponse(
            emotion=data.get("emotion", "CONFUSED"),
            confidence=float(data.get("confidence", 0.5)),
        )
    except (providers.ProviderError, json.JSONDecodeError, ValueError, KeyError):
        pass

    raw = _legacy_generate(_emotion_prompt(req.text), temperature=0.0, json_mode=True)
    if not raw:
        return EmotionResponse(emotion="CONFUSED", confidence=0.0)
    try:
        data = json.loads(raw)
        return EmotionResponse(
            emotion=data.get("emotion", "CONFUSED"),
            confidence=float(data.get("confidence", 0.5)),
        )
    except (json.JSONDecodeError, ValueError):
        return EmotionResponse(emotion="CONFUSED", confidence=0.0)


class MonologueRequest(BaseModel):
    decision: Literal["save_20", "save_33", "save_50"]


class MonologueResponse(BaseModel):
    text: str


@app.post("/joseph-monologue", response_model=MonologueResponse)
def joseph_monologue(req: MonologueRequest) -> MonologueResponse:
    try:
        r = providers.generate(
            purpose="game_branch",
            prompt=_build_prompt("joseph.s2.monologue", {"decision": req.decision}),
            temperature=0.6,
        )
        return MonologueResponse(text=r["text"])
    except providers.ProviderError:
        pass
    text = _legacy_generate(
        _build_prompt("joseph.s2.monologue", {"decision": req.decision}), temperature=0.6
    )
    if not text:
        text = "[mock] 풍요로운 7년 동안 곡식을 저장한다. 그 결정의 무게가 어깨에 얹힌다."
    return MonologueResponse(text=text)


class ReunionRequest(BaseModel):
    decision: Literal["reveal", "test", "silent"]
    distribution_pattern: Literal["farmer_first", "immigrant_first", "merchant_first"]


@app.post("/joseph-reunion", response_model=MonologueResponse)
def joseph_reunion(req: ReunionRequest) -> MonologueResponse:
    vars_ = {"decision": req.decision, "distribution_pattern": req.distribution_pattern}
    try:
        r = providers.generate(
            purpose="game_branch",
            prompt=_build_prompt("joseph.s4.reunion", vars_),
            temperature=0.7,
        )
        return MonologueResponse(text=r["text"])
    except providers.ProviderError:
        pass
    text = _legacy_generate(_build_prompt("joseph.s4.reunion", vars_), temperature=0.7)
    if not text:
        text = "[mock] 형제들이여, 가까이 오라."
    return MonologueResponse(text=text)
