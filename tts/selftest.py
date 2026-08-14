"""빌드 타임 합성 self-test — app.synthesize_to_file 을 그대로 호출한다.

이게 실패하면 docker build 가 실패하고 CI 로그에 정확한 traceback 이 남는다(kubectl 불필요).
성공하면 런타임 /synthesize 도 같은 경로라 동작 보장에 가깝다.

**지원 언어를 전수로 돈다.** `app.SUPPORTED_LANGS` 를 순회하므로, 거기에 언어를 추가하고
여기 문장을 안 넣으면 빌드가 멈춘다 — "지원한다고 적혀만 있고 한 번도 합성해 본 적 없는
언어" 가 생기는 길을 막는다.
"""
import os
import tempfile

import app  # noqa: E402  (import 시 모델 로딩)

# 언어별 검사 문장. app.SUPPORTED_LANGS 와 키가 정확히 일치해야 한다(아래에서 대조).
SAMPLES = {
    "ko": "다윗과 골리앗, 믿음으로 나아가라",
    "en": "David and Goliath — go forward in faith.",
}

missing = [lang for lang in app.SUPPORTED_LANGS if lang not in SAMPLES]
assert not missing, (
    f"[selftest] SUPPORTED_LANGS 에 있으나 검사 문장이 없는 언어: {missing} — "
    "언어를 추가했으면 이 파일의 SAMPLES 에도 문장을 넣어라"
)
extra = [lang for lang in SAMPLES if lang not in app.SUPPORTED_LANGS]
assert not extra, f"[selftest] 지원 목록에 없는 언어의 검사 문장이 남아 있다: {extra}"

# ko 검사 문장은 발음형 변환이 **실제로 발동하는** 문장이어야 한다. 규칙이 하나도 안 걸리는
# 문장을 골라 두면, 변환 경로가 통째로 끊겨도 이 self-test 는 그대로 초록불이 된다.
assert app._spoken(SAMPLES["ko"], "ko") != SAMPLES["ko"], (
    f"[selftest] ko 검사 문장 {SAMPLES['ko']!r} 은 발음형 변환이 한 글자도 바꾸지 않는다 — "
    "연음·경음화가 일어나는 문장으로 바꿔라 (그래야 이 self-test 가 변환 경로를 지난다)"
)
# 캐시 키가 원문이 아니라 *발음형* 으로 잡혀야 한다. 안 그러면 변환을 켜도 예전에 구워 둔
# (철자대로 읽은) wav 를 계속 집어 와서, 배포해도 소리가 안 바뀐다.
assert app._cache_key("걸음도", "narrator-male-low", "ko") == app._cache_key(
    "거름도", "narrator-male-low", "ko"
), "[selftest] 캐시 키가 발음형이 아니라 원문으로 잡혀 있다 — 옛 캐시가 무효화되지 않는다"

print(f"[selftest] speakers={len(app._speakers)} default={app.DEFAULT_SPEAKER} "
      f"voice_map={app.VOICE_MAP} langs={list(app.SUPPORTED_LANGS)}", flush=True)

for lang in app.SUPPORTED_LANGS:
    out = os.path.join(tempfile.gettempdir(), f"selftest-{lang}.wav")
    app.synthesize_to_file(SAMPLES[lang], "narrator-male-low", 1.0, out, lang)

    size = os.path.getsize(out)
    assert size > 1000, f"[selftest][{lang}] wav too small: {size} bytes"
    with open(out, "rb") as f:
        head = f.read(12)
    assert head[:4] == b"RIFF" and head[8:12] == b"WAVE", f"[selftest][{lang}] not a WAV: {head!r}"
    print(f"[selftest][{lang}] OK — wav={size} bytes, RIFF/WAVE verified", flush=True)

# 언어가 다르면 캐시 키도 달라야 한다. 실제 합성 없이 즉시 확인 가능한 축이라 여기서 같이 잰다
# (이 축이 깨지면 en 요청이 ko 오디오를 캐시에서 집어 온다 — 소리로만 드러나는 고장이다).
keys = {lang: app._cache_key("같은 문장", "narrator-male-low", lang) for lang in app.SUPPORTED_LANGS}
assert len(set(keys.values())) == len(keys), f"[selftest] 언어별 캐시 키가 충돌한다: {keys}"
print(f"[selftest] cache keys distinct across {list(keys)} — OK", flush=True)
