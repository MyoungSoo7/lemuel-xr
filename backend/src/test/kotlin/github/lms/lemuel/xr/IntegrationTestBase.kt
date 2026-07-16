package github.lms.lemuel.xr

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트 베이스 — Postgres+pgvector 컨테이너 + Flyway 자동 마이그레이션.
 *
 * AI/TTS 사이드카는 mock URL 로 가리키므로 실제 호출하지 않는 endpoint 만 테스트.
 * (sidecar 호출이 필요한 시나리오는 별도 mock 주입.)
 *
 * **싱글턴 컨테이너 패턴**: 컨테이너를 static 블록(companion object)에서 한 번만 start 하고 stop 하지 않는다
 * (JVM 종료 시 Ryuk 이 정리). `@Testcontainers`/`@Container` 로 클래스별 lifecycle 을 맡기면
 * IT 클래스가 2개 이상일 때 첫 클래스 종료 시 컨테이너가 내려가 두 번째 클래스가
 * "Connection refused" 로 깨진다 — 이를 피하기 위해 수동 싱글턴으로 공유한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
abstract class IntegrationTestBase {

    companion object {
        @JvmStatic
        val POSTGRES: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("lemuel_xr_test")
            .withUsername("xr")
            .withPassword("xr1234")

        init {
            POSTGRES.start() // 한 번만 — stop() 호출 없음, JVM 종료 시 Ryuk 정리.
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { POSTGRES.jdbcUrl }
            r.add("spring.datasource.username") { POSTGRES.username }
            r.add("spring.datasource.password") { POSTGRES.password }
            // 사이드카 mock URL — 실제 호출하지 않는 endpoint 만 테스트 대상.
            r.add("ai.base-url") { "http://localhost:1" }
            r.add("tts.base-url") { "http://localhost:1" }
            r.add("security.rate-limit.enabled") { "false" }
            r.add("chatops.telegram.enabled") { "false" }
        }
    }
}
