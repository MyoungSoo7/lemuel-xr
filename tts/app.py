"""
Lemuel XR — TTS service (Gemini TTS).

백엔드 계약(TtsSynthesisSidecarAdapter)에 정확히 맞춘 사이드카:
  GET  /healthz
  POST /synthesize  { text, voiceId, speakingRate, language } -> { audioUrl, durationMs, engine, language }

audioUrl 은 `data:audio/mpeg;base64,...` 인라인 URL (사이드카 ClusterIP → 브라우저 직접 접근
불가하므로 R2 없이 자족적으로 재생 가능).

**인라인이라 오디오 크기가 곧 전송량이다.** base64 가 33% 를 더 붙이므로 무압축 WAV 를
그대로 실으면 51.5초짜리가 3.30MB 다(프로덕션 최장 행 실측). 그래서 48kbps CBR MP3 로
구워서 싣는다 — 같은 오디오가 0.41MB, 8배 차이다. 자세한 근거는 [_encode_mp3].

## 왜 XTTS-v2 를 버렸는가 (2026-08-15)

XTTS-v2 는 한국어에서 **입력과 무관하게 글에 없는 소리를 계속 만들어냈다.** 154자 문장이
정상 속도면 ~28초인데 47~51초가 나왔고, 중간에 사람 말이 아닌 구간이 섞였다.

처음에는 발음형 전처리(경음화)가 원인인 줄 알았다. 아니었다. **전처리를 아예 거치지 않은
원문도 똑같이 무너졌다** — 대조군을 만들어 재 보고서야 알았다. 구간 길이로 측정한 근거:

    변형              최장 연속 구간    (해당 문구의 정상 길이)
    원문 그대로          6.54s            ~1.9s
    발음형(경음화 포함)   5.05s            ~4.1s
    발음형(경음화 제거)   7.59s            ~4.5s

즉 이건 전처리로 고칠 수 있는 종류의 고장이 아니었다. XTTS-v2 는 다국어 모델이고 한국어는
그 안에서 비중이 작다. 엔진을 바꾸는 것 말고 방법이 없었다.

## Gemini TTS 로 바꾸면서 달라진 것

- **발음형 전처리(korean_g2p)를 없앴다.** Gemini 는 한국어를 아는 모델이라 철자를 그대로
  주면 된다. 발음형을 주면 오히려 틀리게 읽는다 (`애굽에서` 를 `애구베서` 로 주면 그대로
  "애구베서" 라고 읽는다). XTTS 가 한글을 기계적으로 로마자로 옮기던 것에 대한 우회였을 뿐,
  그 엔진과 함께 존재 이유가 사라졌다.
- **모델을 이미지에 굽지 않는다.** 이미지가 ~2GB 에서 수십 MB 로 줄고, 파드 메모리도
  ~2.5GB 에서 수백 MB 로 줄고, 기동 시 모델 로딩(수 분)이 사라진다.
- **`language` 를 강제할 수단이 없어졌다.** Gemini TTS 에는 언어 파라미터가 없고 본문에서
  스스로 판단한다. 그래서 이 값은 이제 *검증과 캐시 키 분리* 에만 쓴다 — 한국어 본문에
  `language=en` 을 줘도 한국어로 읽힌다. XTTS 시절과 달리 여기서 막아 줄 수 없다.
"""
import base64
import hashlib
import json
import os
import shutil
import struct
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

CACHE_DIR = Path(os.getenv("CACHE_DIR", "/data/cache"))
CACHE_DIR.mkdir(parents=True, exist_ok=True)

# 2026-08-15 사용자 청취 비교로 고른 모델. 같은 문장에서 2.5-flash 는 48.5초, 3.1 은 31.3초로
# 읽었다(정상 낭독 길이는 ~30초) — 2.5 계열은 부자연스럽게 늘어진다.
MODEL = os.getenv("GEMINI_TTS_MODEL", "gemini-3.1-flash-tts-preview")

# **preview 모델이다.** 예고 없이 사라질 수 있으므로 env 로 뽑아 두었다. 죽으면 이 값만
# 바꿔 배포하면 된다(코드 변경 불필요). 후보: gemini-2.5-flash-preview-tts.
API_BASE = os.getenv("GEMINI_API_BASE", "https://generativelanguage.googleapis.com/v1beta")

API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
if not API_KEY:
    # 기동 시 죽인다. 키 없이 떠 있으면 /healthz 는 초록불인데 합성만 전부 실패하는,
    # 가장 알아채기 어려운 상태가 된다.
    raise RuntimeError("GEMINI_API_KEY 가 비어 있다 — 이 사이드카는 키 없이는 아무것도 못 한다")

SUPPORTED_LANGS = ("ko", "en")

DEFAULT_LANG = os.getenv("TTS_LANG", "ko")
if DEFAULT_LANG not in SUPPORTED_LANGS:
    raise RuntimeError(
        f"TTS_LANG={DEFAULT_LANG!r} 은 지원 언어가 아니다 (지원: {', '.join(SUPPORTED_LANGS)})"
    )

# 캐시 키의 옛 공간을 소유하는 언어. 이 서비스는 여태 ko 로만 합성했으므로
# `옛 키 공간 == ko 키 공간` 이 성립한다 — 그래서 ko 만 접두 없이 옛 키를 그대로 쓴다.
# 백엔드의 대응 규칙은 SynthesizeTtsUseCase.LEGACY_KEY_LANGUAGE 다. 한쪽만 바꾸면 어긋난다.
LEGACY_KEY_LANG = "ko"

# voiceId -> Gemini prebuilt voice.
#
# **narrator-male-low 만 사람이 귀로 듣고 고른 것이다**(2026-08-15, Charon). 나머지 둘은
# 문서상 성격만 보고 고른 미검증 값이다 — 실제로 쓰기 전에 들어보고 바꿀 것.
VOICE_MAP = {
    "narrator-male-low": os.getenv("VOICE_NARRATOR_MALE_LOW", "Charon"),
    "narrator-female-soft": os.getenv("VOICE_NARRATOR_FEMALE_SOFT", "Achernar"),
    "goliath-bass": os.getenv("VOICE_GOLIATH_BASS", "Algenib"),
}
DEFAULT_VOICE = VOICE_MAP["narrator-male-low"]

# Gemini 는 응답을 **헤더 없는 raw PCM** 으로 준다. mimeType 에 실제 샘플레이트가 실려 오지만
# 모델마다 꼬리 모양이 다르므로(`rate=24000` / `rate=24000; channels=1`) 파싱을 관대하게 한다.
DEFAULT_RATE = 24000

TIMEOUT_S = float(os.getenv("GEMINI_TIMEOUT_SECONDS", "180"))
# 백엔드쪽 상한은 tts.timeout-seconds(기본 300s)다. 여기 값은 그보다 낮게 둔다 — 그래야
# 실패가 우리 쪽 명시적 에러로 돌아가고, 백엔드가 먼저 끊어 원인 불명이 되지 않는다.

MAX_ATTEMPTS = int(os.getenv("GEMINI_MAX_ATTEMPTS", "3"))

# MP3 인코딩 — 근거는 [_encode_mp3] docstring 참조.
LAME_BIN = os.getenv("LAME_BIN", "lame")
MP3_BITRATE_KBPS = int(os.getenv("MP3_BITRATE_KBPS", "48"))

if shutil.which(LAME_BIN) is None:
    # API 키와 같은 이유로 기동 시 죽인다. 인코더가 없으면 /healthz 는 초록불인데
    # 합성 요청만 전부 502 가 되는, 가장 알아채기 어려운 상태가 된다.
    raise RuntimeError(f"{LAME_BIN!r} 을 찾을 수 없다 — 이 사이드카는 mp3 인코더 없이는 못 굽는다")


def _rate_from_mime(mime: str) -> int:
    """`audio/L16;codec=pcm;rate=24000` / `audio/l16; rate=24000; channels=1` 둘 다 받는다."""
    if "rate=" not in mime:
        return DEFAULT_RATE
    try:
        return int(mime.split("rate=")[-1].split(";")[0].strip())
    except ValueError:
        return DEFAULT_RATE


def _pcm_to_wav(pcm: bytes, rate: int, path: str) -> None:
    """16-bit mono PCM 에 WAV 헤더를 붙인다. lame 에 먹이는 중간 산물이다."""
    with open(path, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt ")
        f.write(struct.pack("<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16))
        f.write(b"data" + struct.pack("<I", len(pcm)))
        f.write(pcm)


def _encode_mp3(pcm: bytes, rate: int, path: str) -> None:
    """PCM 을 MP3(CBR mono)로 인코딩한다.

    나레이션은 `data:` URL 로 **본문에 통째로 실려** 브라우저까지 간다. base64 가 33% 를
    더 붙이므로 무압축 WAV 는 그대로 전송량이 된다 — 프로덕션 최장 행 실측으로
    51.5초짜리가 base64 3.30MB 였다. 같은 오디오가 48kbps MP3 로는 0.41MB 다(8배).

    비트레이트를 48k 로 둔 이유: 32k 면 11배까지 줄지만 24kHz 사람 목소리에서 아티팩트가
    들리기 시작하는 지점이다. 이 프로젝트는 XTTS 를 *소리 품질 때문에* 버렸다 — 여기서
    다시 소리를 깎아 되돌아갈 이유가 없다. 8배면 충분하다.

    `-t` 는 LAME/Xing 태그 프레임을 안 쓰게 한다. 그 프레임이 있으면 [_duration_ms] 가
    파일 크기로 재는 길이가 한 프레임(24ms) 만큼 더 길게 나온다.
    """
    tmp_wav = f"{path}.tmp.wav"
    try:
        _pcm_to_wav(pcm, rate, tmp_wav)
        proc = subprocess.run(
            [LAME_BIN, "--quiet", "-t", "--cbr", "-m", "m", "-b", str(MP3_BITRATE_KBPS),
             tmp_wav, path],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            # 여기서 조용히 넘어가면 0바이트 파일이 캐시에 남고, 다음 요청이 그걸 히트해
            # "재생은 되는데 소리가 없는" 상태가 된다. 실패는 실패로 올린다.
            raise RuntimeError(
                f"mp3 인코딩 실패 (lame rc={proc.returncode}): {proc.stderr.strip()[:300]}"
            )
    finally:
        Path(tmp_wav).unlink(missing_ok=True)


def _call_gemini(text: str, voice: str) -> tuple[bytes, int]:
    """본문을 그대로 보내고 (PCM, samplerate) 를 받는다.

    **본문에 스타일 지시문을 앞에 붙이지 않는다.** ("차분하게 읽어줘: ..." 같은 것)
    2026-08-15 에 사용자가 귀로 고른 샘플이 지시문 없는 상태에서 나온 소리다. 지시문을
    붙이면 그 소리가 아니게 된다. 낭독 톤을 바꾸고 싶으면 목소리부터 바꿔 볼 것.
    """
    body = json.dumps({
        "contents": [{"parts": [{"text": text}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}},
        },
    }).encode()

    last: Exception | None = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        req = urllib.request.Request(
            f"{API_BASE}/models/{MODEL}:generateContent?key={API_KEY}",
            data=body, headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT_S) as r:
                payload = json.load(r)
            break
        except urllib.error.HTTPError as e:
            detail = e.read()[:500].decode("utf-8", "replace")
            # 429(쿼터)·5xx 는 재시도할 값어치가 있다. 4xx 나머지는 우리 요청이 틀린 것이라
            # 재시도해도 같은 답이 온다 — 즉시 올린다.
            if e.code != 429 and e.code < 500:
                raise RuntimeError(f"Gemini {e.code}: {detail}") from e
            last = RuntimeError(f"Gemini {e.code}: {detail}")
        except (urllib.error.URLError, TimeoutError) as e:
            last = e
        if attempt < MAX_ATTEMPTS:
            time.sleep(2 ** attempt)
    else:
        raise RuntimeError(f"Gemini 호출이 {MAX_ATTEMPTS}회 모두 실패했다: {last}")

    cand = (payload.get("candidates") or [{}])[0]
    parts = (cand.get("content") or {}).get("parts") or []
    audio, rate = b"", DEFAULT_RATE
    for p in parts:
        inline = p.get("inlineData")
        if inline:
            audio += base64.b64decode(inline["data"])
            rate = _rate_from_mime(inline.get("mimeType", ""))

    if not audio:
        # 오디오 대신 텍스트가 오거나(모델이 지시로 오해) 안전 필터에 걸린 경우. 조용히
        # 빈 wav 를 내보내면 "재생은 되는데 소리가 없는" 상태가 되므로 명시적으로 실패시킨다.
        said = " ".join(p.get("text", "") for p in parts)[:200]
        raise RuntimeError(
            f"Gemini 가 오디오를 주지 않았다 (finishReason={cand.get('finishReason')}, "
            f"safety={cand.get('safetyRatings')}, text={said!r})"
        )
    return audio, rate


def synthesize_to_file(
    text: str, voice_id: str, rate: float, out_path: str, lang: str = DEFAULT_LANG
) -> None:
    """합성 단일 경로. 엔드포인트와 빌드 selftest 가 공유한다.

    NOTE: `speakingRate` 는 여전히 무시한다. Gemini TTS 에는 속도 파라미터가 없고, 본문에
    지시문을 섞는 방식뿐인데 그건 위에서 쓰지 않기로 한 방법이다. 속도 조절이 필요해지면
    ffmpeg atempo 후처리로 붙일 것.
    """
    if lang not in SUPPORTED_LANGS:
        raise ValueError(f"unsupported language: {lang!r} (지원: {', '.join(SUPPORTED_LANGS)})")
    voice = VOICE_MAP.get(voice_id) or DEFAULT_VOICE
    pcm, sample_rate = _call_gemini(text, voice)
    _encode_mp3(pcm, sample_rate, out_path)
    print(
        f"[lemuel-xr-tts] synthesized model={MODEL} voice={voice} lang={lang} "
        f"chars={len(text)} pcm={len(pcm)}B rate={sample_rate} "
        f"mp3={Path(out_path).stat().st_size}B",
        flush=True,
    )


app = FastAPI(title="lemuel-xr-tts")


class SynthesizeRequest(BaseModel):
    text: str
    voiceId: str = "narrator-male-low"
    speakingRate: float | None = None
    language: str = DEFAULT_LANG


def _cache_key(text: str, voice_id: str, lang: str) -> str:
    """언어가 다르면 키도 달라야 한다 — 같은 문장·같은 화자라도 오디오가 다르기 때문이다.

    ko 만 접두 없이 옛 키를 그대로 쓴다([LEGACY_KEY_LANG] 참조).

    다만 그 이득이 어디서 나오는지는 분명히 해 둔다. **이 서비스의 /data/cache 는
    emptyDir 이라 파드가 갈리면 어차피 전부 사라진다** — 롤아웃을 건너서 지켜지는 캐시가
    아니다. 옛 키를 보존해서 실제로 값을 버는 쪽은 영속인 백엔드 캐시(Postgres TtsCache)
    이고, 여기서 같은 규칙을 지키는 이유는 양쪽 키 공간을 일치시키기 위해서다.

    2026-08-15: 키 재료에서 발음형 변환이 빠졌다(엔진 교체로 전처리 자체가 사라졌다).
    그래서 **같은 문장의 키가 예전과 달라진다** — 옛 wav 는 자연히 미아가 되고 다시 구워진다.
    백엔드 캐시도 같이 무효화해야 소리가 실제로 바뀐다: SynthesizeTtsUseCase.AUDIO_GENERATION.

    확장자만 `.mp3` 로 바뀌었고 해시 재료는 그대로다. 여기 캐시는 emptyDir 이라 확장자
    변경으로 옛 파일이 미아가 되는 것은 실질적 손해가 없다 — 영속 캐시는 백엔드 쪽이다.
    """
    prefix = "" if lang == LEGACY_KEY_LANG else f"{lang}::"
    return hashlib.sha256(f"{prefix}{voice_id}::{text}".encode("utf-8")).hexdigest() + ".mp3"


def _duration_ms(path: Path | str) -> int:
    """CBR MP3 의 길이를 파일 크기로 잰다.

    프레임 헤더를 걷지 않는 이유는, [_encode_mp3] 가 CBR 로 굽고 `-t` 로 Xing 태그
    프레임까지 없앴기 때문이다 — 그러면 파일은 같은 크기의 프레임만 늘어선 것이라
    `bytes * 8 / bitrate` 가 곧 길이다.

    **약 +56ms 만큼 길게 나온다** (실측: 1000ms 입력 -> 1056ms, 51520ms -> 51576ms).
    입력 길이와 무관한 고정 편차이고, 정체는 lame 의 인코더 지연(1152 샘플 = 48ms)이
    프레임 경계(24ms)로 올림된 것이다. 빼서 보정하지 않는 이유는, 그 값이 인코더 구현
    세부라 lame 판이 바뀌면 보정식이 조용히 틀려지기 때문이다 — 재지 않은 상수를 빼는
    것보다 잰 편차를 적어 두는 편이 정직하다.

    애초에 이 값은 계약상 돌려주는 메타데이터일 뿐 재생 동작에 쓰이지 않는다(프론트는
    `durationMs` 를 타입으로만 들고 다닌다). 56ms 를 위해 프레임 파서를 들일 값어치가 없다.
    """
    try:
        return int(os.path.getsize(path) * 8 * 1000 / (MP3_BITRATE_KBPS * 1000))
    except OSError:
        return 0


@app.get("/healthz")
def healthz():
    return {
        "status": "ok",
        "model": MODEL,
        "voices": sorted(set(VOICE_MAP.values())),
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
        try:
            synthesize_to_file(text, req.voiceId, req.speakingRate or 1.0, str(fpath), lang)
        except RuntimeError as e:
            # 실패한 흔적을 캐시에 남기면 다음 요청이 빈 파일을 히트한다.
            fpath.unlink(missing_ok=True)
            raise HTTPException(status_code=502, detail=str(e)) from e

    audio_b64 = base64.b64encode(fpath.read_bytes()).decode("ascii")
    return {
        "audioUrl": f"data:audio/mpeg;base64,{audio_b64}",
        "durationMs": _duration_ms(fpath),
        "engine": MODEL,
        # 어떤 언어로 구웠는지 돌려준다. 다만 Gemini 는 언어를 본문에서 스스로 판단하므로
        # 이 값은 "우리가 그렇게 분류해서 캐시했다" 는 뜻이지 "그 언어로 읽혔다" 는 보장이 아니다.
        "language": lang,
    }
