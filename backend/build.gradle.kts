plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "github.lms.lemuel"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 4 — web + jpa + validation + actuator + cache + scheduler + webflux(WebClient)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // WebClient (AI/TTS 사이드카)
    implementation("org.springframework.boot:spring-boot-starter-security")  // JWT 인증 필터

    // Postgres + Flyway + pgvector
    runtimeOnly("org.postgresql:postgresql")
    // ⚠️ Spring Boot 4 부터 Flyway autoconfig 가 별도 모듈로 분리 — starter 필수.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.pgvector:pgvector:0.1.4")  // Java pgvector binding

    // JSONB 는 Hibernate native @JdbcTypeCode(SqlTypes.JSON) 사용 (기존 코드 컨벤션)

    // JWT — jjwt (HS256, 30일 만료)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Caffeine cache (LLM 응답 in-memory 1차 캐시; 2차는 llm_cache 테이블)
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Micrometer Prometheus — /actuator/prometheus endpoint (Grafana 대시보드용)
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // OpenTelemetry traces — Spring → OTLP exporter → Tempo
    // (Micrometer Tracing 이 trace_id 를 모든 로그 MDC 에 자동 박아 logs↔traces correlation)
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // YAML 시나리오 로더
    implementation("org.yaml:snakeyaml")

    // ShedLock — @Scheduled 의 분산 락 (replicas N 개 중 1 개만 실행 보장)
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Testcontainers — Postgres+pgvector 통합 테스트
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
}

// === UTF-8 일관성 강제 ===========================================
// 모든 한국어 텍스트(성경 본문·묵상문·시나리오)·로그·JSON 응답이
// 빌드/런타임 어느 단계에서도 깨지지 않도록 5곳 모두 UTF-8.
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
    finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.withType<org.gradle.api.tasks.bundling.Jar> {
    manifest {
        attributes("Implementation-Encoding" to "UTF-8")
    }
}
tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
}
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    manifest {
        attributes("Implementation-Encoding" to "UTF-8")
    }
}
