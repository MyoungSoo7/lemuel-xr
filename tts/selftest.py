"""빌드 타임 self-test.

이게 실패하면 docker build 가 실패하고 CI 로그에 정확한 traceback 이 남는다(kubectl 불필요).

## 왜 빌드에서 진짜 합성을 하지 않는가 (2026-08-15)

XTTS 시절에는 모델이 이미지 안에 있어서 빌드 중에 실제로 한 문장을 합성해 볼 수 있었다.
Gemini 로 바꾸면서 그게 불가능해졌다 — 합성하려면 **API 키를 빌드에 넣어야** 하고, 그러면
지금 클러스터 시크릿에만 있는 키가 GitHub Actions 에도 생긴다(노출면·회전 대상 증가).
게다가 빌드마다 쿼터를 쓴다.

그래서 여기서는 **네트워크를 타지 않는 부분을 전부** 검사하고, 실제 합성은 `--live` 로
분리했다. `--live` 는 키가 있는 곳(로컬·배포 검증)에서 수동으로 돌린다:

    GEMINI_API_KEY=... python selftest.py --live

배포 후 실제 소리 확인은 파드에 대고 한 번 /synthesize 를 때려 보는 것으로 대신한다.
"""
import os
import struct
import sys
import tempfile
import wave

os.environ.setdefault("GEMINI_API_KEY", "selftest-placeholder")  # import 시 기동 검사를 통과시킨다
os.environ.setdefault("CACHE_DIR", tempfile.mkdtemp())

import app  # noqa: E402

LIVE = "--live" in sys.argv

# ── 1. PCM -> WAV 헤더 ────────────────────────────────────────────────────────
# Gemini 는 헤더 없는 raw PCM 을 준다. 헤더를 잘못 붙이면 "요청은 200 인데 브라우저에서
# 재생이 안 되는" 상태가 되고, 그건 로그에 아무 흔적도 남기지 않는다.
pcm = b"\x00\x01" * 24000  # 1초치 16-bit mono
out = os.path.join(tempfile.gettempdir(), "selftest.wav")
app._pcm_to_wav(pcm, 24000, out)

with open(out, "rb") as f:
    head = f.read(12)
assert head[:4] == b"RIFF" and head[8:12] == b"WAVE", f"[selftest] not a WAV: {head!r}"

with wave.open(out, "rb") as w:
    assert w.getnchannels() == 1, f"[selftest] channels={w.getnchannels()} (mono 여야 한다)"
    assert w.getsampwidth() == 2, f"[selftest] sampwidth={w.getsampwidth()} (16-bit 여야 한다)"
    assert w.getframerate() == 24000, f"[selftest] rate={w.getframerate()}"
    assert w.getnframes() == 24000, f"[selftest] frames={w.getnframes()}"

# 헤더에 적은 길이와 실제 파일 크기가 어긋나면 일부 플레이어가 뒤를 잘라 먹는다.
size = os.path.getsize(out)
with open(out, "rb") as f:
    riff = struct.unpack("<I", f.read(8)[4:])[0]
assert riff == size - 8, f"[selftest] RIFF 길이 {riff} != 파일 {size}-8"
assert app._duration_ms(out) == 1000, f"[selftest] duration={app._duration_ms(out)}ms (1000 이어야)"
print(f"[selftest] wav header OK — {size} bytes, 1000ms", flush=True)

# ── 2. mimeType 파싱 ──────────────────────────────────────────────────────────
# 모델마다 꼬리가 다르다. 실제로 2.5 는 `rate=24000`, 3.1 은 `rate=24000; channels=1` 을 준다
# — 후자를 int() 에 그대로 넣어 ValueError 로 죽은 적이 있다.
for mime, expect in [
    ("audio/L16;codec=pcm;rate=24000", 24000),
    ("audio/l16; rate=24000; channels=1", 24000),
    ("audio/L16;codec=pcm;rate=16000", 16000),
    ("audio/L16", app.DEFAULT_RATE),
    ("audio/L16;rate=nonsense", app.DEFAULT_RATE),
]:
    got = app._rate_from_mime(mime)
    assert got == expect, f"[selftest] {mime!r} -> {got}, expected {expect}"
print("[selftest] mimeType 파싱 OK", flush=True)

# ── 3. 캐시 키 ────────────────────────────────────────────────────────────────
# 언어가 다르면 키도 달라야 한다. 이 축이 깨지면 en 요청이 ko 오디오를 집어 온다 —
# 소리로만 드러나는 고장이다.
keys = {lang: app._cache_key("같은 문장", "narrator-male-low", lang) for lang in app.SUPPORTED_LANGS}
assert len(set(keys.values())) == len(keys), f"[selftest] 언어별 캐시 키가 충돌한다: {keys}"

# 화자가 다르면 키도 달라야 한다(같은 문장을 다른 목소리로 요청하면 다른 오디오다).
assert app._cache_key("문장", "narrator-male-low", "ko") != app._cache_key(
    "문장", "goliath-bass", "ko"
), "[selftest] 화자가 달라도 캐시 키가 같다"

# ko 는 접두 없이 옛 키 공간을 그대로 쓴다([app.LEGACY_KEY_LANG]).
assert app._cache_key("문장", "narrator-male-low", "ko").startswith(
    __import__("hashlib").sha256(b"narrator-male-low::\xeb\xac\xb8\xec\x9e\xa5").hexdigest()
), "[selftest] ko 키에 언어 접두가 붙었다 — 옛 키 공간과 어긋난다"
print("[selftest] 캐시 키 분리 OK", flush=True)

# ── 4. 목소리 매핑 ────────────────────────────────────────────────────────────
# 백엔드가 보내는 voiceId 3종이 전부 매핑돼 있어야 한다. 빠지면 조용히 기본 목소리로
# 떨어져서, 골리앗이 내레이터 목소리로 말하는 걸 귀로 알아챌 때까지 모른다.
for vid in ("narrator-male-low", "narrator-female-soft", "goliath-bass"):
    assert app.VOICE_MAP.get(vid), f"[selftest] voiceId {vid!r} 이 매핑되지 않았다"
assert len(set(app.VOICE_MAP.values())) == len(app.VOICE_MAP), (
    f"[selftest] 서로 다른 voiceId 가 같은 목소리를 가리킨다: {app.VOICE_MAP}"
)
print(f"[selftest] voice_map={app.VOICE_MAP} model={app.MODEL} OK", flush=True)

# ── 5. 지원하지 않는 언어는 합성 경로에 들어가지 못한다 ───────────────────────
try:
    app.synthesize_to_file("hola", "narrator-male-low", 1.0, "/tmp/x.wav", "es")
except ValueError:
    pass
else:  # pragma: no cover
    raise AssertionError("[selftest] 지원하지 않는 언어가 합성 경로를 통과했다")
print("[selftest] 언어 검증 OK", flush=True)

if not LIVE:
    print("[selftest] 통과 (네트워크 미사용 — 실제 합성은 --live)", flush=True)
    raise SystemExit(0)

# ── 6. --live: 진짜로 합성해 본다 ─────────────────────────────────────────────
assert os.environ["GEMINI_API_KEY"] != "selftest-placeholder", (
    "[selftest] --live 에는 진짜 GEMINI_API_KEY 가 필요하다"
)
SAMPLES = {"ko": "다윗과 골리앗, 믿음으로 나아가라.", "en": "David and Goliath — go forward in faith."}
missing = [l for l in app.SUPPORTED_LANGS if l not in SAMPLES]
assert not missing, f"[selftest] 검사 문장이 없는 언어: {missing}"

for lang in app.SUPPORTED_LANGS:
    path = os.path.join(tempfile.gettempdir(), f"selftest-{lang}.wav")
    app.synthesize_to_file(SAMPLES[lang], "narrator-male-low", 1.0, path, lang)
    ms = app._duration_ms(path)
    assert ms > 500, f"[selftest][{lang}] 너무 짧다: {ms}ms"
    print(f"[selftest][{lang}] live OK — {os.path.getsize(path)} bytes, {ms}ms", flush=True)
