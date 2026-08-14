"""
Lemuel XR — TTS service (Coqui XTTS-v2).

백엔드 계약(TtsSidecarClient)에 정확히 맞춘 사이드카:
  GET  /healthz
  POST /synthesize  { text, voiceId, speakingRate, language } -> { audioUrl, durationMs, engine, language }

`language` 는 "ko" 와 "en" 만 받는다([SUPPORTED_LANGS]). 모르는 값은 400 이다 — 조용히
기본 언어로 떨어뜨리지 않는다. 요청한 언어와 다른 언어가 돌아오는 것이 이 서비스에서
가장 알아채기 어려운 고장이기 때문이다.

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

# XTTS-v2 자체는 더 많은 언어를 안다. 그런데 이 서비스가 "지원한다"고 말할 수 있는 범위는
# **빌드 selftest 가 실제로 합성해 본 언어까지**다(`selftest.py`). 그래서 둘로 못 박는다.
# 늘리려면 SUPPORTED_LANGS 와 selftest 를 같이 늘려야 한다 — 한쪽만 늘리면 검증 안 된
# 언어를 지원한다고 주장하게 된다.
SUPPORTED_LANGS = ("ko", "en")

DEFAULT_LANG = os.getenv("TTS_LANG", "ko")
if DEFAULT_LANG not in SUPPORTED_LANGS:
    raise RuntimeError(
        f"TTS_LANG={DEFAULT_LANG!r} 은 지원 언어가 아니다 (지원: {', '.join(SUPPORTED_LANGS)})"
    )

# 캐시 키의 옛 공간을 소유하는 언어. 이 서비스는 여태 ko 로만 합성했으므로
# `옛 키 공간 == ko 키 공간` 이 성립한다 — 그래서 ko 만 접두 없이 옛 키를 그대로 쓴다.
# DEFAULT_LANG 이 아니라 "ko" 리터럴에 묶어 둔 이유: 나중에 TTS_LANG 을 en 으로 바꾸면
# en 이 옛 ko 파일들을 자기 것으로 착각하고 집어 들게 된다.
LEGACY_KEY_LANG = "ko"

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


def synthesize_to_file(
    text: str, voice_id: str, rate: float, out_path: str, lang: str = DEFAULT_LANG
) -> None:
    """합성 단일 경로. 엔드포인트와 빌드 selftest 가 공유한다.

    XTTS-v2 는 multi-speaker 라 speaker 지정이 필수. speaker 는 언어와 무관하게 고를 수 있다
    (XTTS 는 다국어 음색이라 같은 화자로 ko 도 en 도 낸다) — 그래서 VOICE_MAP 은 언어별로
    나누지 않는다.
    NOTE: tts_to_file 에 speed= 를 넘기면 이 XTTS 버전에서 합성이 30s(백엔드 타임아웃)를 넘겨
    502 를 유발한다(2026-07-14 확인). 따라서 speed 는 넘기지 않는다(정상속도). speakingRate 는
    추후 ffmpeg atempo 후처리(빠름)로 별도 구현 예정 — rate 인자는 현재 무시.
    """
    if lang not in SUPPORTED_LANGS:
        # 엔드포인트가 먼저 걸러 주지만 여기서도 막는다 — selftest 가 이 함수를 직접 부르므로
        # 검증되지 않은 언어가 빌드 단계로 새 들어오는 길을 함께 닫는다.
        raise ValueError(f"unsupported language: {lang!r} (지원: {', '.join(SUPPORTED_LANGS)})")
    speaker = VOICE_MAP.get(voice_id) or DEFAULT_SPEAKER
    if not speaker:
        raise RuntimeError("no XTTS speaker available (resolved 0 speakers)")
    tts_engine.tts_to_file(
        text=text,
        file_path=out_path,
        language=lang,
        speaker=speaker,
    )


app = FastAPI(title="lemuel-xr-tts")


class SynthesizeRequest(BaseModel):
    text: str
    voiceId: str = "narrator-male-low"
    speakingRate: float | None = None
    language: str = DEFAULT_LANG


def _cache_key(text: str, voice_id: str, lang: str) -> str:
    """언어가 다르면 키도 달라야 한다 — 같은 문장·같은 화자라도 오디오가 다르기 때문이다.

    ko 만 접두 없이 옛 키를 그대로 쓴다([LEGACY_KEY_LANG] 참조). 전부에 접두를 붙이면
    이미 구워 둔 캐시가 한 번에 통째로 무효가 되고, 다음 요청부터 전부 재합성한다.

    다만 그 이득이 어디서 나오는지는 분명히 해 둔다. **이 서비스의 /data/cache 는
    emptyDir 이라 파드가 갈리면 어차피 전부 사라진다** — 롤아웃을 건너서 지켜지는 캐시가
    아니다. 옛 키를 보존해서 실제로 값을 버는 쪽은 영속인 백엔드 캐시(Postgres TtsCache)
    이고, 여기서 같은 규칙을 지키는 이유는 양쪽 키 공간을 일치시키기 위해서다.
    백엔드의 대응 규칙은 SynthesizeTtsUseCase.LEGACY_KEY_LANGUAGE 다. 한쪽만 바꾸면
    두 캐시가 어긋난다.

    (2026-08-15 정정: 이 자리에 "캐시(PVC)" 라고 적혀 있었다. PVC 가 아니다.
     실제로 이 커밋 직후의 롤아웃에서 구워져 있던 wav 50개·64MB 가 그대로 날아갔다.)
    """
    prefix = "" if lang == LEGACY_KEY_LANG else f"{lang}::"
    return hashlib.sha256(f"{prefix}{voice_id}::{text}".encode("utf-8")).hexdigest() + ".wav"


def _duration_ms(path: Path) -> int:
    try:
        with wave.open(str(path), "rb") as w:
            rate = w.getframerate() or 1
            return int(w.getnframes() * 1000 / rate)
    except Exception:  # noqa: BLE001
        return 0


@app.get("/healthz")
def healthz():
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "speakers": len(_speakers),
        "languages": list(SUPPORTED_LANGS),
        "defaultLanguage": DEFAULT_LANG,
    }


@app.post("/synthesize")
def synthesize(req: SynthesizeRequest):
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is empty")

    lang = (req.language or DEFAULT_LANG).strip().lower()
    if lang not in SUPPORTED_LANGS:
        raise HTTPException(
            status_code=400,
            detail=f"unsupported language: {req.language!r} (지원: {', '.join(SUPPORTED_LANGS)})",
        )

    fpath = CACHE_DIR / _cache_key(text, req.voiceId, lang)
    if not fpath.exists():
        synthesize_to_file(text, req.voiceId, req.speakingRate or 1.0, str(fpath), lang)

    audio_b64 = base64.b64encode(fpath.read_bytes()).decode("ascii")
    return {
        "audioUrl": f"data:audio/wav;base64,{audio_b64}",
        "durationMs": _duration_ms(fpath),
        "engine": "xtts-v2",
        # 어떤 언어로 구웠는지 돌려준다. 이게 없으면 호출자는 자기가 보낸 값이 반영됐는지
        # 오디오를 귀로 듣기 전에는 알 수 없다 — 이 필드가 없던 동안 language 는 계속 무시됐다.
        "language": lang,
    }
