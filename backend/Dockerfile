###########################
# Stage 1: Build
###########################
# ⚠️ 빌드 컨텍스트는 **리포 루트** 다(backend/ 아님).
#    골든셋 원본이 리포 루트 `eval/grounding/` 에 있고 backend/build.gradle.kts 가
#    이를 `../eval/grounding` 으로 참조하기 때문이다. 컨텍스트를 backend 로 두면
#    그 디렉터리가 이미지 안에 존재하지 않고, Gradle 의 from(...) 은 없는 디렉터리에
#    대해 조용히 0건을 복사하며 성공한다 → 골든셋 없는 jar 가 배포된다.
#    (2026-08-05 실제 사고. build.gradle.kts 에 존재 검사와 jar 검증을 함께 걸어 뒀다.)
#    CI 는 `context: .` + `file: backend/Dockerfile` 로 부른다.
FROM gradle:9.1.0-jdk25 AS builder
WORKDIR /workspace

# rootProject 가 /workspace 이므로 `../eval/grounding` 은 /eval/grounding 으로 풀린다.
COPY eval/grounding /eval/grounding

COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon bootJar -x test

RUN find /workspace/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

###########################
# Stage 2: Runtime
###########################
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl tini && \
    addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--", "java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
