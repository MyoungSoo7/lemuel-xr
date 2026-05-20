"""
Lemuel XR — TTS service.

Coqui XTTS-v2 multilingual TTS 를 FastAPI 로 래핑.
요청 받은 텍스트를 한국어 음성 wav 로 생성, 캐시 후 반환.

엔드포인트:
  GET  /healthz                  → 200 OK
  POST /tts   { text, voice_id } → wav 바이너리 (cached)

캐시 키: sha256(text + voice_id). 첫 호출은 모델 추론, 이후 호출은 디스크 캐시.
"""
import hashlib
import os
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel

CACHE_DIR = Path(os.getenv("CACHE_DIR", "/data/cache"))
CACHE_DIR.mkdir(parents=True, exist_ok=True)

MODEL_NAME = os.getenv("MODEL", "tts_models/multilingual/multi-dataset/xtts_v2")

# Coqui TTS 는 import 시 모델 다운로드 + 로딩이라 시간이 걸린다.
# 모듈 레벨에서 한 번만 로딩해 reuse.
print(f"[lemuel-xr-tts] loading model: {MODEL_NAME}")
from TTS.api import TTS  # noqa: E402

tts_engine = TTS(MODEL_NAME)
print("[lemuel-xr-tts] model loaded")

app = FastAPI(title="lemuel-xr-tts")


class TTSRequest(BaseModel):
    text: str
    voice_id: str = "default"
    language: str = "ko"


def cache_key(text: str, voice_id: str, language: str) -> str:
    h = hashlib.sha256(f"{voice_id}::{language}::{text}".encode("utf-8")).hexdigest()
    return f"{h}.wav"


@app.get("/healthz")
def healthz():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/tts")
def synthesize(req: TTSRequest):
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="text is empty")

    fname = cache_key(req.text, req.voice_id, req.language)
    fpath = CACHE_DIR / fname

    if not fpath.exists():
        # Coqui XTTS-v2 는 voice reference (speaker_wav) 가 필요.
        # MVP 첫 라운드는 multilingual 기본 보이스 — speaker_wav 생략 시 합성기 기본.
        tts_engine.tts_to_file(
            text=req.text,
            file_path=str(fpath),
            language=req.language,
        )

    return FileResponse(fpath, media_type="audio/wav", filename=fname)
