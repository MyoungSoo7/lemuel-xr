# Coqui 모델 다운로드 서버가 speakers_xtts.pth 를 HTML 로 반환(_pickle.UnpicklingError:
# invalid load key, '<')하며 지속 실패한다(2026-07-14). GitHub Actions 는 레이어 캐시가
# 런간 유지되지 않아 매 빌드가 모델(~1.87GB)을 재다운로드하므로, 다운로드 장애 시 빌드 자체가
# 막힌다.
#
# 해결: 모델+의존성(torch2.5.1 CPU, TTS, transformers)이 이미 baked-in 된 "마지막 정상 이미지"를
# 베이스로 삼고 app 레이어만 덮어쓴다. → Coqui 다운로드에 의존하지 않고, app 반복이 빨라지며,
# 노드도 베이스 레이어를 이미 캐시해 pull 이 가볍다. (아래 base 는 audio 재생 검증된 이미지.)
#
# Coqui 다운로드가 복구되면 전체 빌드(python:3.11-slim + 모델 bake)로 되돌릴 수 있다 —
# git history 의 fe09f84 이전 Dockerfile 참조. 장기적으로는 model+deps 전용 -base 이미지 분리 권장.
FROM ghcr.io/myoungsoo7/lemuel-xr-tts:main-6e83f1397c6250fa505a0e5f9231502c33455889

WORKDIR /app

# app 코드만 갱신 (모델/deps/ENV(COQUI_TOS_AGREED)는 베이스에서 상속)
COPY app.py selftest.py .

# 빌드 타임 합성 self-test — /synthesize 와 동일 경로(synthesize_to_file, speed 포함) 실행.
# 실패 시 build 실패 + CI 로그에 traceback → kubectl 없이 원인 파악. 성공 = 런타임 동작 보장에 근접.
RUN python selftest.py

EXPOSE 5002
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=10 \
  CMD curl -fsS http://localhost:5002/healthz || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "5002"]
