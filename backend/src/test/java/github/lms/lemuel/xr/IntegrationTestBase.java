package github.lms.lemuel.xr;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 베이스 — Postgres+pgvector 컨테이너 + Flyway 자동 마이그레이션.
 *
 * <p>AI/TTS 사이드카는 mock URL 로 가리키므로 실제 호출하지 않는 endpoint 만 테스트.
 * (sidecar 호출이 필요한 시나리오는 별도 mock 주입.)</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("lemuel_xr_test")
            .withUsername("xr")
            .withPassword("xr1234");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        // 사이드카 mock URL — 실제 호출하지 않는 endpoint 만 테스트 대상.
        r.add("ai.base-url", () -> "http://localhost:1");
        r.add("tts.base-url", () -> "http://localhost:1");
        r.add("security.rate-limit.enabled", () -> "false");
        r.add("chatops.telegram.enabled", () -> "false");
    }
}
