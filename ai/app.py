"""
Lemuel XR — AI orchestration service.

backend (Spring Boot) 가 호출하는 LLM 사이드카. OpenAI 직접 호출 + 향후 LangChain·LangGraph 도입.

엔드포인트:
  GET  /healthz
  POST /classify-emotion   { text } → { emotion, confidence }
  POST /joseph-monologue   { decision } → { text }   # Scene 2/3 분기 독백
  POST /joseph-reunion     { decision, distribution_pattern } → { text }   # Scene 4 실시간
"""
import json
import os
from typing import Literal

from fastapi import FastAPI
from openai import OpenAI
from pydantic import BaseModel

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")

client = OpenAI(api_key=OPENAI_API_KEY)
app = FastAPI(title="lemuel-xr-ai")

# -------------------------------------------------------------------
# 1. 감정 분류
# -------------------------------------------------------------------
EMOTION_PROMPT = """
너는 한국어 감정 분류기다. 다음 사용자 텍스트를 7개 감정 중 하나로 분류하라.

가능한 감정:
ANXIOUS    - 불안, 두려움, 걱정
SAD        - 슬픔, 우울, 상실
ANGRY      - 분노, 짜증, 답답함
CONFUSED   - 혼란, 막막함
LONELY     - 외로움, 고립감
EXHAUSTED  - 지침, 번아웃, 무기력
GRATEFUL   - 감사, 평안

응답은 JSON 한 줄: {"emotion": "ANXIOUS", "confidence": 0.85}
설명·여백 없이 JSON 만 출력.
"""

Emotion = Literal["ANXIOUS", "SAD", "ANGRY", "CONFUSED", "LONELY", "EXHAUSTED", "GRATEFUL"]


class EmotionRequest(BaseModel):
    text: str


class EmotionResponse(BaseModel):
    emotion: Emotion
    confidence: float


@app.get("/healthz")
def healthz():
    return {"status": "ok", "model": MODEL}


@app.post("/classify-emotion", response_model=EmotionResponse)
def classify_emotion(req: EmotionRequest) -> EmotionResponse:
    if not OPENAI_API_KEY:
        # 키 없으면 CONFUSED fallback — 로컬 개발 편의
        return EmotionResponse(emotion="CONFUSED", confidence=0.0)

    rsp = client.chat.completions.create(
        model=MODEL,
        messages=[
            {"role": "system", "content": EMOTION_PROMPT},
            {"role": "user", "content": req.text},
        ],
        response_format={"type": "json_object"},
        temperature=0.0,
    )
    raw = rsp.choices[0].message.content or "{}"
    try:
        data = json.loads(raw)
        return EmotionResponse(
            emotion=data.get("emotion", "CONFUSED"),
            confidence=float(data.get("confidence", 0.5)),
        )
    except (json.JSONDecodeError, ValueError):
        return EmotionResponse(emotion="CONFUSED", confidence=0.0)


# -------------------------------------------------------------------
# 2. 요셉 Scene 2/3 분기 독백 (사전 캐시 가능)
# -------------------------------------------------------------------
JOSEPH_MONOLOGUE_PROMPT = """
당신은 청년 요셉입니다. 이집트 총리로서 7년 풍년 동안 전체 곡식의 {percentage}% 를 저장하기로 결정했습니다.
당신의 내면 독백을 한국어 2~3 문장으로 작성하세요. 백성을 향한 책임감과 두려움이 섞여 있어야 합니다.
"""


class MonologueRequest(BaseModel):
    decision: Literal["save_20", "save_33", "save_50"]


class MonologueResponse(BaseModel):
    text: str


_MONO_PCT = {"save_20": "20", "save_33": "33", "save_50": "50"}


@app.post("/joseph-monologue", response_model=MonologueResponse)
def joseph_monologue(req: MonologueRequest) -> MonologueResponse:
    pct = _MONO_PCT[req.decision]
    if not OPENAI_API_KEY:
        return MonologueResponse(text=f"[mock] 풍요로운 7년 동안 {pct}% 를 저장한다. 그 결정의 무게가 어깨에 얹힌다.")
    rsp = client.chat.completions.create(
        model=MODEL,
        messages=[{"role": "user", "content": JOSEPH_MONOLOGUE_PROMPT.format(percentage=pct)}],
        temperature=0.6,
    )
    return MonologueResponse(text=(rsp.choices[0].message.content or "").strip())


# -------------------------------------------------------------------
# 3. 요셉 Scene 4 실시간 — 형제 재회 대사 (Scene 3 분배 패턴 반영)
# -------------------------------------------------------------------
REUNION_PROMPT = """
당신은 청년 요셉, 이집트 총리입니다.
당신은 7년 흉년에 곡식을 우선적으로 {priority} 에게 분배했습니다.
지금 야곱의 아들들 (당신의 형제들) 이 곡식을 구하러 와 당신 앞에 절합니다.
당신은 그들에게 {action} 을 선택했습니다.

이 상황에서 요셉이 형제들에게 하는 첫 마디를 한국어 한 줄로 작성하세요.
{priority} 우선 분배라는 사실이 톤에 영향을 줍니다. 신학적·정치적 함의 포함.
"""


class ReunionRequest(BaseModel):
    decision: Literal["reveal", "test", "silent"]
    distribution_pattern: Literal["farmer_first", "immigrant_first", "merchant_first"]


_DISTRIBUTION = {
    "farmer_first": "이집트 농민",
    "immigrant_first": "이주민 가족",
    "merchant_first": "무역 상인",
}
_ACTION = {
    "reveal": "정체를 즉시 밝히는 것",
    "test": "잠시 시험하는 것 (베냐민 데려오라 요청)",
    "silent": "침묵하는 것",
}


@app.post("/joseph-reunion", response_model=MonologueResponse)
def joseph_reunion(req: ReunionRequest) -> MonologueResponse:
    priority = _DISTRIBUTION[req.distribution_pattern]
    action = _ACTION[req.decision]
    if not OPENAI_API_KEY:
        return MonologueResponse(text=f"[mock] ({priority} 우선/{action}) 형제들이여, 가까이 오라.")
    rsp = client.chat.completions.create(
        model=MODEL,
        messages=[{"role": "user", "content": REUNION_PROMPT.format(priority=priority, action=action)}],
        temperature=0.7,
    )
    return MonologueResponse(text=(rsp.choices[0].message.content or "").strip())
