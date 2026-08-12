// `java` 는 plugins 블록의 Gradle 확장에 가려져 패키지로 해석되지 않는다.
// 아래 bootJar 검증에서 쓰므로 최상단에서 명시적으로 import 한다.
import java.util.zip.ZipFile

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

    // ContentSafetyGateEnforcementTest 는 리포 루트의 `content/{인물}/scene*.yml` 을 읽는다.
    // 그 디렉터리는 백엔드 소스도 클래스패스도 아니라서 Gradle 이 입력으로 보지 못하고,
    // 저작 YAML 만 고치면 :test 가 UP-TO-DATE 로 건너뛴다 — 안전 게이트가 조용히 낡는다.
    // 입력으로 등록해 콘텐츠가 바뀌면 반드시 다시 돌게 한다.
    inputs.dir(rootProject.layout.projectDirectory.dir("../content"))
        .withPropertyName("authoringContent")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // 같은 이유로 `docs/` 도 입력이다. 문서를 계약으로 읽는 테스트가 여럿 있다 —
    // CrisisKeywordDocContractTest(docs/EMOTION-CLASSIFIER.md),
    // RuntimeExposureSignoffTest(docs/RUTH-RUNTIME-SIGNOFF.md 의 사인오프 두 줄).
    //
    // 2026-08-12 에 이걸로 한 번 속았다: 사인오프 대장을 고쳐 놓고 :test 를 돌렸는데
    // Gradle 이 UP-TO-DATE 로 건너뛰고 **직전 실행의 초록을 그대로 다시 보여줬다.**
    // 게이트를 무력화하는 편집을 해 놓고 초록을 받은 셈이라, 판정기를 mutation 으로
    // 검증하는 절차 자체가 무의미해진다. CI 는 매번 새 체크아웃이라 안 겪지만,
    // 그건 로컬에서 거짓 초록을 봐도 된다는 뜻이 아니다.
    inputs.dir(rootProject.layout.projectDirectory.dir("../docs"))
        .withPropertyName("designDocs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// === 근거성 골든셋을 jar 에 넣는다 ======================================
// 원본은 리포 루트 `eval/grounding/` 이다(백엔드 리소스가 아닌 이유는 그곳 README §1).
// 배포된 파드가 스스로 골든셋을 채점하려면 데이터가 아티팩트 안에 있어야 하므로 복사한다.
// `from(...)` 이 이 디렉터리를 태스크 입력으로 선언하므로, 픽스처만 고쳐도
// processResources → test 가 다시 돈다(무결성 검증을 건너뛴 표본이 남지 않는다).
//
// ⚠️ 원본 디렉터리가 없으면 Gradle 의 `from(...)` 은 **조용히 0건을 복사하고 성공한다**.
//    2026-08-05 운영에서 정확히 그렇게 터졌다: Docker 빌드 컨텍스트가 `backend` 라
//    리포 루트의 `eval/grounding` 이 이미지 안에 아예 없었고, 빌드는 초록불로 통과했으며,
//    골든셋 없는 jar 가 배포돼 첫 채점이 "manifest 없음" 으로 실패했다.
//    그래서 아래에서 존재를 강제하고, bootJar 결과물까지 열어 확인한다.
val goldenSetSrc = rootProject.layout.projectDirectory.dir("../eval/grounding")

tasks.named<ProcessResources>("processResources") {
    val srcDir = goldenSetSrc.asFile
    doFirst {
        check(srcDir.isDirectory) {
            "골든셋 원본이 없다: ${srcDir.absolutePath} — Docker 빌드라면 컨텍스트가 리포 루트인지, " +
                "Dockerfile 이 eval/grounding 을 COPY 하는지 확인할 것. " +
                "여기서 멈추지 않으면 골든셋 없는 jar 가 조용히 만들어진다."
        }
    }
    from(goldenSetSrc) {
        into("grounding-golden-set")
        include("v*/**")   // README 등 사람이 읽는 문서는 아티팩트에 넣지 않는다
    }
}

// 산출물 검증 — "복사 태스크가 돌았다" 와 "jar 안에 있다" 는 다른 명제다.
// 실제로 배포되는 아티팩트를 열어서 확인한다. 파드가 읽는 그 경로 그대로.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    doLast {
        val jar = archiveFile.get().asFile
        val entries = ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("BOOT-INF/classes/grounding-golden-set/") }
                .toList()
        }
        check(entries.any { it.endsWith("/manifest.json") }) {
            "bootJar 에 골든셋 manifest 가 없다: ${jar.name} — 배포하면 채점이 " +
                "'골든셋 manifest 없음' 으로 실패한다(2026-08-05 재발 방지)."
        }
        val fixtures = entries.count { it.contains("/fixtures/") && it.endsWith(".json") }
        check(fixtures > 0) { "bootJar 에 골든셋 픽스처가 0건이다: ${jar.name}" }
        logger.lifecycle("골든셋 jar 포함 확인: manifest 1, 픽스처 ${fixtures}건")
    }
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
//
// 2026-07-29 CI 실측(Java 25, Testcontainers 포함 전체 스위트):
//   LINE 98.27% (covered 3796 / missed 67) · BRANCH 86.17% (covered 430 / missed 69)
// 하한은 각각 95% / 80% — 정상적인 변동은 흡수하되 유의미한 후퇴는 잡는 폭이다.
// LINE 1%p 는 약 39줄, BRANCH 1%p 는 약 5분기에 해당한다.
val coverageMinimum = (project.findProperty("coverageMinimum") as String? ?: "0.95").toBigDecimal()
val coverageBranchMinimum = (project.findProperty("coverageBranchMinimum") as String? ?: "0.80").toBigDecimal()

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coverageMinimum
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = coverageBranchMinimum
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
