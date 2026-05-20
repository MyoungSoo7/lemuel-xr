###########################
# Stage 1: Build
###########################
FROM gradle:9.1.0-jdk25 AS builder
WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src

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
