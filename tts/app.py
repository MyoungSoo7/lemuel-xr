"""
Lemuel XR — TTS service (Coqui XTTS-v2).

백엔드 계약(TtsSidecarClient)에 정확히 맞춘 사이드카:
  GET  /healthz
  POST /synthesize  { text, voiceId, speakingRate } -> { audioUrl, durationMs, engine }

audioUrl 은 `data:audio/wav;base64,...` 인라인 URL 로 반환한다.
사이드카는 ClusterIP 라 브라우저가 직접 못 오므로, 별도 오브젝트 스토리지(R2) 업로드/
프록시 없이 자족적으로 재생 가능한 data URL 이 가장 단순하고 확실하다. (R2 업로드는 추후 최적화.)

모델은 Dockerfile 빌드 단계에서 이미지에 baked-in → 런타임 다운로드가 전혀 없다
(emptyDir 재다운로드 doom loop 원천 차단). 부팅 시엔 baked 모델을 RAM 으로 로딩만 한다.
"""
import base64
import hashlib
import os
import wave
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

CACHE_DIR = Path(os.getenv("CACHE_DIR", "/data/cache"))
CACHE_DIR.mkdir(parents=True, exist_ok=True)

MODEL_NAME = os.getenv("MODEL", "tts_models/multilingual/multi-dataset/xtts_v2")
DEFAULT_LANG = os.getenv("TTS_LANG", "ko")

# Coqui TTS 는 import + 생성자에서 모델을 로딩한다. baked 모델이라 다운로드는 없고 로딩만.
print(f"[lemuel-xr-tts] loading model: {MODEL_NAME}", flush=True)
from TTS.api import TTS  # noqa: E402

tts_engine = TTS(MODEL_NAME)
print("[lemuel-xr-tts] model loaded", flush=True)

# --- speaker 해석 — XTTS-v2 는 multi-speaker 라 speaker 지정이 필수(없으면 합성 실패) ---
try:
    _speakers = list(tts_engine.speakers or [])
except Exception:  # noqa: BLE001
    _speakers = []


def _pick(*prefs):
    """선호 speaker 중 실제 존재하는 첫 번째, 없으면 첫 번째 사용 가능 speaker."""
    for p in prefs:
        if p in _speakers:
            return p
    return _speakers[0] if _speakers else None


VOICE_MAP = {
    "narrator-male-low": _pick("Damien Black", "Baldur Sanjin", "Viktor Eka", "Andrew Chipper"),
    "narrator-female-soft": _pick("Gracie Wise", "Tammie Ema", "Daisy Studious", "Claribel Dervla"),
    "goliath-bass": _pick("Baldur Sanjin", "Damien Black", "Viktor Eka"),
}
DEFAULT_SPEAKER = _pick("Damien Black", "Claribel Dervla")
print(f"[lemuel-xr-tts] speakers={len(_speakers)} default={DEFAULT_SPEAKER}", flush=True)

app = FastAPI(title="lemuel-xr-tts")


class SynthesizeRequest(BaseModel):
    text: str
    voiceId: str = "narrator-male-low"
    speakingRate: float | None = None
    language: str = DEFAULT_LANG


def _cache_key(text: str, voice_id: str, rate: float) -> str:
    raw = f"{voice_id}::{rate}::{text}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest() + ".wav"


def _duration_ms(path: Path) -> int:
    try:
        with wave.open(str(path), "rb") as w:
            rate = w.getframerate() or 1
            return int(w.getnframes() * 1000 / rate)
    except Exception:  # noqa: BLE001
        return 0


@app.get("/healthz")
def healthz():
    return {"status": "ok", "model": MODEL_NAME, "speakers": len(_speakers)}


@app.post("/synthesize")
def synthesize(req: SynthesizeRequest):
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is empty")

    speaker = VOICE_MAP.get(req.voiceId) or DEFAULT_SPEAKER
    rate = req.speakingRate if (req.speakingRate and req.speakingRate > 0) else 1.0

    fpath = CACHE_DIR / _cache_key(text, req.voiceId, rate)
    if not fpath.exists():
        kwargs = {"text": text, "file_path": str(fpath), "language": req.language}
        if speaker:
            kwargs["speaker"] = speaker
        try:
            tts_engine.tts_to_file(speed=rate, **kwargs)  # XTTS 는 speed 지원
        except TypeError:
            tts_engine.tts_to_file(**kwargs)  # speed 미지원 버전 fallback

    audio_b64 = base64.b64encode(fpath.read_bytes()).decode("ascii")
    return {
        "audioUrl": f"data:audio/wav;base64,{audio_b64}",
        "durationMs": _duration_ms(fpath),
        "engine": "xtts-v2",
    }
