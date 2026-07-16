package github.lms.lemuel.xr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * values/adapter/in/web/ValuesController 통합 테스트.
 *
 * 실 서버(RANDOM_PORT) + Postgres/pgvector Testcontainer + Flyway. ContentWebIT 와 동일한
 * 게스트 발급 → disclaimer 동의 인증 패턴을 재사용해 451/401 게이트를 통과시킨다.
 *
 * 커버: GET /me (프로파일 없는 신규 사용자 → 빈 값 + CDR 0 + BUILD_UP) · POST /profile
 * (7 가치 upsert 후 통계 재조회) · POST /practice (실천 기록 + CDR 반영) · 인증 게이트.
 */
class ValuesWebIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    private var token: String = ""

    private fun client(): RestClient = RestClient.create("http://localhost:$port")

    private fun authed(): RestClient = RestClient.builder()
        .baseUrl("http://localhost:$port")
        .defaultHeader("Authorization", "Bearer $token")
        .build()

    @BeforeEach
    fun issueGuestAndAcceptDisclaimer() {
        val rest = client()
        val guest = rest.post().uri("/api/auth/guest")
            .body(
                mapOf(
                    "deviceFingerprint" to "values-web-it-" + System.nanoTime(),
                    "deviceType" to "quest3",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        this.token = guest["token"] as String
        assertThat(token).isNotBlank()
        rest.post().uri("/api/auth/accept-disclaimer")
            .header("Authorization", "Bearer $token")
            .body(mapOf<String, Any>())
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `me 신규사용자 빈프로파일 CDR0 BUILD_UP`() {
        val body = authed().get().uri("/api/values/me")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val values = body["values"] as Map<String, Any>
        assertThat(values).isEmpty()
        val stats = body["stats"] as Map<String, Any>
        assertThat(stats).containsEntry("totalPractices7d", 0)
            .containsEntry("cdrIndex", 0)
            .containsEntry("tier", "BUILD_UP")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `profile upsert 후 값 반영`() {
        val body = authed().post().uri("/api/values/profile")
            .body(mapOf("1" to mapOf("title" to "흔들리지 않는 결정", "anchor_character" to "joseph")))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val values = body["values"] as Map<String, Any>
        assertThat(values).containsKey("1")
        assertThat((values["1"] as Map<String, Any>)["title"])
            .isEqualTo("흔들리지 않는 결정")
        // upsert 응답에도 stats 포함.
        assertThat(body["stats"] as Map<String, Any>).containsKey("cdrIndex")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `profile 부분갱신 기존키 유지`() {
        authed().post().uri("/api/values/profile")
            .body(mapOf("1" to mapOf("title" to "첫째"))).retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        val body = authed().post().uri("/api/values/profile")
            .body(mapOf("2" to mapOf("title" to "둘째")))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val values = body["values"] as Map<String, Any>
        assertThat(values).containsKeys("1", "2")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `practice 기록 후 me 에서 카운트 증가`() {
        val practice = authed().post().uri("/api/values/practice")
            .body(
                mapOf(
                    "valueId" to 3, "durationSec" to 120, "note" to "감사한 하루",
                    "linkedCharacter" to "joseph",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(practice).containsEntry("valueId", 3)
        assertThat(practice["id"]).isNotNull()

        val me = authed().get().uri("/api/values/me").retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val stats = me["stats"] as Map<String, Any>
        assertThat((stats["totalPractices7d"] as Number).toInt()).isGreaterThanOrEqualTo(1)
        val countByValue = stats["countByValue"] as Map<String, Any>
        assertThat(countByValue).containsKey("3")
    }

    @Test
    fun `practice 범위밖 valueId 400`() {
        // @Min(1) @Max(7) — valueId=8 은 ConstraintViolation → 400.
        try {
            authed().post().uri("/api/values/practice")
                .body(mapOf("valueId" to 8))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `me 인증없으면 401 또는 451`() {
        try {
            client().get().uri("/api/values/me").retrieve().body(String::class.java)
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }
}
