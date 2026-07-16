package github.lms.lemuel.xr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient

/**
 * AuthController 프로필 PATCH(safety / ai-opt-out) + /api/users/me disclaimer 상태 +
 * JourneyController(weekly / today) 통합 테스트.
 *
 * 기존 IT 가 다루지 않은 auth/web(70%)·journey/web(0%) 커버.
 */
class AuthProfileAndJourneyWebIT : IntegrationTestBase() {

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
                    "deviceFingerprint" to "auth-journey-it-" + System.nanoTime(),
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

    // ─────────────────────────── /api/users/me disclaimer 상태 ───────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `me disclaimer동의후 accepted true`() {
        val me = authed().get().uri("/api/users/me").retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(me).containsEntry("userType", "guest")
        val disclaimer = me["disclaimer"] as Map<String, Any>
        assertThat(disclaimer).containsEntry("accepted", true)
        assertThat(disclaimer["currentVersion"]).isEqualTo("1.0")
        assertThat(me["aiOptOut"]).isEqualTo(false)
    }

    // ─────────────────────────── PATCH /api/users/me/safety ───────────────────────────

    @Test
    fun `patch safety 설정 반영`() {
        val body = authed().patch().uri("/api/users/me/safety")
            .body(
                mapOf(
                    "hapticIntensity" to "high", "skipIntroSilence" to true,
                    "dataRetentionDays" to 45,
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("hapticIntensity", "high")
            .containsEntry("skipIntroSilence", true)
            .containsEntry("dataRetentionDays", 45)
    }

    // ─────────────────────────── PATCH /api/users/me/ai-opt-out ───────────────────────────

    @Test
    fun `patch aiOptOut true후 me에 반영`() {
        val opt = authed().patch().uri("/api/users/me/ai-opt-out")
            .body(mapOf("optOut" to true))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(opt).containsEntry("optOut", true)

        val me = authed().get().uri("/api/users/me").retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(me["aiOptOut"]).isEqualTo(true)
    }

    @Test
    fun `patch aiOptOut null false처리`() {
        val opt = authed().patch().uri("/api/users/me/ai-opt-out")
            .body(mapOf<String, Any>()) // optOut 없음 → false.
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(opt).containsEntry("optOut", false)
    }

    // ─────────────────────────── /api/journey/weekly ───────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `journey weekly 신규사용자 week0 joseph`() {
        val body = authed().get().uri("/api/journey/weekly").retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        // 실천 이력 없는 신규 게스트 → week 0.
        assertThat(body).containsEntry("weekIndex", 0).containsEntry("character", "joseph")
        assertThat(body["theme"] as String).contains("신중함")
        assertThat(body["values"] as List<Int>).containsExactly(1, 2)
    }

    // ─────────────────────────── /api/journey/today ───────────────────────────

    @Test
    fun `journey today 신규사용자 valueId1`() {
        val body = authed().get().uri("/api/journey/today").retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("weekIndex", 0).containsEntry("character", "joseph")
            .containsEntry("valueId", 1)
        // 카드 시드 없으면 null, 있으면 map — 존재 여부만 확인 (NPE 없이 응답).
        assertThat(body).containsKey("card")
    }
}
