FROM python:3.11-slim

# Coqui TTS 의존성 (libsndfile, espeak-ng for phoneme)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libsndfile1 espeak-ng ffmpeg curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Coqui TTS 모델 라이선스 자동 수락 — CPML (research only, but ok for non-commercial demo)
ENV COQUI_TOS_AGREED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

EXPOSE 5002
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=10 \
  CMD curl -fsS http://localhost:5002/healthz || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "5002"]
