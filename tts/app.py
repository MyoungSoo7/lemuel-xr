"""
Lemuel XR — TTS service (Coqui XTTS-v2).

백엔드 계약(TtsSidecarClient)에 정확히 맞춘 사이드카:
  GET  /healthz
  POST /synthesize  { text, voiceId, speakingRate } -> { audioUrl, durationMs, engine }

audioUrl 은 `data:audio/wav;base64,...` 인라인 URL (사이드카 ClusterIP → 브라우저 직접 접근
불가하므로 R2 없이 자족적으로 재생 가능). 모델은 Dockerfile 빌드 단계에서 이미지에 baked-in.

합성 경로는 synthesize_to_file() 하나로 모으고, Dockerfile 의 selftest.py 가 빌드 시 이 함수를
그대로 호출한다 → 합성 오류가 CI 로그에 드러나 kubectl 없이도 원인 파악·수정 가능.
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

print(f"[lemuel-xr-tts] loading model: {MODEL_NAME}", flush=True)
from TTS.api import TTS  # noqa: E402

tts_engine = TTS(MODEL_NAME)
print("[lemuel-xr-tts] model loaded", flush=True)


def _resolve_speakers():
    """XTTS 내장 speaker 목록을 여러 api 경로로 시도해 얻는다(버전차 흡수)."""
    getters = (
        lambda: tts_engine.speakers,
        lambda: tts_engine.synthesizer.tts_model.speaker_manager.speaker_names,
        lambda: list(tts_engine.synthesizer.tts_model.speaker_manager.speakers.keys()),
    )
    for g in getters:
        try:
            s = list(g() or [])
            if s:
                return s
        except Exception:  # noqa: BLE001
            continue
    return []


_speakers = _resolve_speakers()


def _pick(*prefs):
    for p in prefs:
        if p in _speakers:
            return p
    return _speakers[0] if _speakers else None


VOICE_MAP = {
    "narrator-male-low": _pick("Damien Black", "Baldur Sanjin", "Viktor Eka", "Andrew Chipper"),
    "narrator-female-soft": _pick("Gracie Wise", "Tammie Ema", "Daisy Studious", "Claribel Dervla"),
    "goliath-bass": _pick("Baldur Sanjin", "Damien Black", "Viktor Eka"),
}
DEFAULT_SPEAKER = _pick("Ana Florence", "Claribel Dervla", "Damien Black")
print(f"[lemuel-xr-tts] speakers={len(_speakers)} default={DEFAULT_SPEAKER}", flush=True)


def synthesize_to_file(text: str, voice_id: str, rate: float, out_path: str) -> None:
    """합성 단일 경로. 엔드포인트와 빌드 selftest 가 공유한다.

    XTTS-v2 는 multi-speaker 라 speaker 지정이 필수.
    NOTE: tts_to_file 에 speed= 를 넘기면 이 XTTS 버전에서 합성이 30s(백엔드 타임아웃)를 넘겨
    502 를 유발한다(2026-07-14 확인). 따라서 speed 는 넘기지 않는다(정상속도). speakingRate 는
    추후 ffmpeg atempo 후처리(빠름)로 별도 구현 예정 — rate 인자는 현재 무시.
    """
    speaker = VOICE_MAP.get(voice_id) or DEFAULT_SPEAKER
    if not speaker:
        raise RuntimeError("no XTTS speaker available (resolved 0 speakers)")
    tts_engine.tts_to_file(
        text=text,
        file_path=out_path,
        language=DEFAULT_LANG,
        speaker=speaker,
    )


app = FastAPI(title="lemuel-xr-tts")


class SynthesizeRequest(BaseModel):
    text: str
    voiceId: str = "narrator-male-low"
    speakingRate: float | None = None
    language: str = DEFAULT_LANG


def _cache_key(text: str, voice_id: str) -> str:
    return hashlib.sha256(f"{voice_id}::{text}".encode("utf-8")).hexdigest() + ".wav"


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

    fpath = CACHE_DIR / _cache_key(text, req.voiceId)
    if not fpath.exists():
        synthesize_to_file(text, req.voiceId, req.speakingRate or 1.0, str(fpath))

    audio_b64 = base64.b64encode(fpath.read_bytes()).decode("ascii")
    return {
        "audioUrl": f"data:audio/wav;base64,{audio_b64}",
        "durationMs": _duration_ms(fpath),
        "engine": "xtts-v2",
    }
