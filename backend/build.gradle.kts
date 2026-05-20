plugins {
    java
    // Spring AI 1.0 GA 가 아직 Spring Boot 4 와 호환 안 됨 (RestClientAutoConfiguration 경로 변경)
    // SB 3.5 LTS 로 시작, AI 가 SB4 지원하면 그때 업그레이드.
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "github.lms.lemuel"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 4
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Postgres + Flyway
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Spring AI 1.0 GA (artifact 이름이 spring-ai-starter-model-openai 로 바뀜)
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Caffeine cache (LLM 응답 캐시)
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
