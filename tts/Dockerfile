FROM python:3.11-slim

# Coqui TTS 의존성 (libsndfile, espeak-ng for phoneme)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libsndfile1 espeak-ng ffmpeg curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Coqui TTS 모델 라이선스 자동 수락 — CPML (research only, but ok for non-commercial demo)
ENV COQUI_TOS_AGREED=1

COPY requirements.txt .
# 클러스터엔 GPU 가 없다(CPU 추론). 기본 PyPI 의 torch 는 CUDA 휠(~2.5GB + nvidia 라이브러리)
# 이라 이미지가 ~7GB 로 불어 CI 디스크·홈노드 pull(디스크 압박)에 위험. CPU 전용 휠로 설치해
# 이미지를 ~3GB 로 낮춘다. requirements.txt 도 동일 버전 핀이라 아래 -r 설치 시 재설치 없음.
RUN pip install --no-cache-dir torch==2.5.1 torchaudio==2.5.1 \
      --index-url https://download.pytorch.org/whl/cpu \
 && pip install --no-cache-dir -r requirements.txt

# 모델(~1.87GB)을 빌드 타임에 이미지로 굽는다 — 런타임 다운로드 완전 제거.
# emptyDir 재다운로드 doom loop(louise 노드 압박)의 원천 차단. app.py 보다 먼저 실행해
# 코드만 바뀔 때 이 무거운 레이어가 캐시되도록 한다. COQUI_TOS_AGREED=1(위 ENV)로 무프롬프트.
# 이 RUN 이 성공하면 transformers 핀/의존성 스택이 import 가능함도 함께 증명된다.
RUN python -c "from TTS.api import TTS; TTS('tts_models/multilingual/multi-dataset/xtts_v2')"

COPY app.py .

EXPOSE 5002
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=10 \
  CMD curl -fsS http://localhost:5002/healthz || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "5002"]
