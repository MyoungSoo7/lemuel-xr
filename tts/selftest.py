"""빌드 타임 합성 self-test — app.synthesize_to_file 을 그대로 호출한다.

이게 실패하면 docker build 가 실패하고 CI 로그에 정확한 traceback 이 남는다(kubectl 불필요).
성공하면 런타임 /synthesize 도 같은 경로라 동작 보장에 가깝다.
"""
import os
import tempfile

import app  # noqa: E402  (import 시 모델 로딩)

out = os.path.join(tempfile.gettempdir(), "selftest.wav")
print(f"[selftest] speakers={len(app._speakers)} default={app.DEFAULT_SPEAKER} "
      f"voice_map={app.VOICE_MAP}", flush=True)

app.synthesize_to_file("다윗과 골리앗, 믿음으로 나아가라", "narrator-male-low", 1.0, out)

size = os.path.getsize(out)
assert size > 1000, f"[selftest] wav too small: {size} bytes"
with open(out, "rb") as f:
    head = f.read(12)
assert head[:4] == b"RIFF" and head[8:12] == b"WAVE", f"[selftest] not a WAV: {head!r}"
print(f"[selftest] OK — wav={size} bytes, RIFF/WAVE verified", flush=True)
