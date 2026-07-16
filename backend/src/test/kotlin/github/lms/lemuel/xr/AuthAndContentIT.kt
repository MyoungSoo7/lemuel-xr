package github.lms.lemuel.xr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * 핵심 E2E 시나리오:
 * 1) 게스트 사용자 발급 → JWT
 * 2) /api/users/me (인증 보호)
 * 3) /api/content/topics (공개)
 * 4) /api/safety/crisis-resources (공개 — 한국어 시드)
 * 5) /api/scripture/gen-45:5 (창세기 본문 한국어 UTF-8)
 * 6) /api/game/joseph/start (인증 보호, scenarios/joseph.yml 로드)
 */
class AuthAndContentIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    private fun client(): RestClient = RestClient.create("http://localhost:$port")

    @Test
    fun guest_token_then_authenticated_paths() {
        val rest = client()

        // 1. 게스트 발급
        val guest = rest.post().uri("/api/auth/guest")
            .body(mapOf("deviceFingerprint" to "it-1", "deviceType" to "quest3"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(guest).containsKey("token").containsKey("userId")
        val token = guest["token"] as String
        assertThat(token).isNotBlank()

        // 1.5 Disclaimer 동의 — DisclaimerGateFilter 통과를 위해 필수
        rest.post().uri("/api/auth/accept-disclaimer")
            .header("Authorization", "Bearer $token")
            .body(mapOf<String, Any>())
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})

        // 2. /api/users/me
        val me = rest.get().uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(me).containsKey("userId").containsEntry("userType", "guest")

        // 3. /api/content/topics — 7주제 + 한국어
        val topics = rest.get().uri("/api/content/topics")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val topicList = topics["topics"] as List<*>
        assertThat(topicList).hasSize(7)
        assertThat(topics.toString()).contains("일기와 묵상").contains("사람을 두려워하지 않는 것")

        // 4. /api/safety/crisis-resources — V7 시드
        val crisis = rest.get().uri("/api/safety/crisis-resources?region=KR&locale=ko-KR")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val resources = crisis["resources"] as List<*>
        assertThat(resources).hasSizeGreaterThanOrEqualTo(4)
        assertThat(resources.toString()).contains("1393").contains("자살예방")

        // 5. /api/scripture/gen-45:5 — V2 시드
        val scripture = rest.get().uri("/api/scripture/gen-45:5")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(scripture).containsEntry("reference", "gen-45:5")
        assertThat(scripture["text"] as String).contains("하나님")

        // 6. /api/game/joseph/start — Scene 1 "파라오의 꿈"
        val start = rest.post().uri("/api/game/joseph/start")
            .header("Authorization", "Bearer $token")
            .body(mapOf("mode" to "emotional"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(start).containsEntry("character", "joseph")
            .containsEntry("currentScene", 1)
        @Suppress("UNCHECKED_CAST")
        val payload = start["scenePayload"] as Map<String, Any>
        assertThat(payload["title"] as String).contains("파라오")
    }

    @Test
    fun no_token_protected_path_unauthorized() {
        try {
            client().get().uri("/api/users/me").retrieve().body(String::class.java)
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403)
        }
    }
}
