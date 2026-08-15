# 2026-08-15: XTTS-v2 를 버리고 Gemini TTS 로 바꾸면서 이 Dockerfile 이 통째로 단순해졌다.
#
# 예전에는 Coqui 모델(~1.87GB)을 이미지에 굽느라, 다운로드 서버가 speakers_xtts.pth 를
# HTML 로 돌려주는 장애(2026-07-14)에 빌드가 통째로 막혔고, 그걸 우회하려고 "마지막 정상
# 이미지" 를 base 로 물려 app 레이어만 덮어쓰는 구조를 썼다. 모델이 없어지면서 그 우회도
# 함께 사라졌다 — 이제 평범한 python:3.11-slim 빌드다.
#
# 부수 효과: 이미지가 ~2GB -> 수십 MB, 파드 메모리 ~2.5GB -> 수백 MB, 기동 시 모델 로딩
# (수 분) 이 사라져 첫 Ready 까지가 초 단위가 된다.
FROM python:3.11-slim

WORKDIR /app

# healthcheck 가 curl 을 쓴다. 이미지가 작아진 만큼 이 한 줄이 눈에 띄므로 남겨 둔다 —
# 빼려면 HEALTHCHECK 를 python 으로 바꿔야 한다.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py selftest.py .

# 네트워크를 타지 않는 전 구간 검사(WAV 헤더·mime 파싱·캐시 키·목소리 매핑·언어 검증).
# 실제 합성은 API 키가 필요해 빌드에서 하지 않는다 — 이유는 selftest.py docstring 참조.
RUN python selftest.py

EXPOSE 5002
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost:5002/healthz || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "5002"]
