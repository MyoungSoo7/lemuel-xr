plugins {
    java
    jacoco
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.jpa") version "2.2.20"
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

    // Kotlin — 전체 소스 Kotlin (Java→Kotlin 100% 마이그레이션 완료; Lombok 제거됨)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Testcontainers — Postgres+pgvector 통합 테스트
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    // Kotlin 친화 Mockito — `when` 키워드 충돌 회피(whenever), reified mock()
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

// === Kotlin =====================================================
kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// Kotlin 2.2.x 는 JDK 25 를 아직 JVM target 으로 지원하지 않아 JVM_24 로 폴백한다.
// Java 는 25 바이트코드를, Kotlin 은 24 바이트코드를 내는데 JVM 25 는 24 를 문제없이
// 로드하므로 이 target 일관성 검증만 warning 으로 완화한다 (Kotlin 이 25 지원 시 제거).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    jvmTargetValidationMode.set(org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.WARNING)
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

// 커버리지 하한. 감으로 정하지 않고 실측값에서 여유를 뺀 래칫이다 —
// 내려가면 빌드가 깨지고, 올리는 건 이 값만 고치면 된다.
val coverageMinimum = (project.findProperty("coverageMinimum") as String? ?: "0.0").toBigDecimal()

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coverageMinimum
            }
        }
    }
}

// 측정값을 CI 로그에 남긴다. 래칫을 올릴 때 근거가 되고,
// 게이트가 깨졌을 때 얼마나 모자란지 바로 보인다.
tasks.register("printCoverage") {
    dependsOn(tasks.named("jacocoTestReport"))
    val reportXml = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
    doLast {
        val xml = reportXml.get().asFile
        if (!xml.exists()) {
            println("coverage: 리포트 없음 ($xml)")
            return@doLast
        }
        // report 루트의 합계 counter 들이 파일 끝에 온다.
        Regex("""<counter type="(\w+)" missed="(\d+)" covered="(\d+)"/>""")
            .findAll(xml.readText())
            .toList()
            .takeLast(6)
            .forEach { m ->
                val (type, missed, covered) = m.destructured
                val total = missed.toLong() + covered.toLong()
                val pct = if (total == 0L) 0.0 else covered.toLong() * 100.0 / total
                println("coverage %-12s %6.2f%%  covered=%s missed=%s".format(type, pct, covered, missed))
            }
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
