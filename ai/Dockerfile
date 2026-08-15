# CI 의 ai 잡(.github/workflows/ci.yml)과 **같은 파이썬**이어야 한다. 2026-08-15 이전에는
# 테스트가 3.12, 이미지가 3.11 이라 같은 코드가 서로 다른 런타임에서 돌았다. 게이트 잡이
# 쓰는 3.13 으로 셋을 맞췄다. 여기를 올리면 ci.yml 의 ai 잡도 같이 올릴 것.
FROM python:3.13-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .
COPY providers.py .

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost:8000/healthz || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
