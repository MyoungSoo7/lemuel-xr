package github.lms.lemuel.xr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * game/adapter/in/web/GameController 통합 테스트.
 *
 * 4 인물(joseph/moses/david/jesus) + job 시나리오 로딩, /start → /decide → /complete
 * 전체 흐름과 에러 분기(잘못된 character/세션/완료된 세션)를 실 서버로 검증.
 * ContentWebIT 의 게스트 발급 → disclaimer 동의 인증 패턴을 재사용.
 */
class GameWebIT : IntegrationTestBase() {

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
                    "deviceFingerprint" to "game-web-it-" + System.nanoTime(),
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

    @Suppress("UNCHECKED_CAST")
    private fun start(character: String, mode: String?): Map<String, Any> =
        authed().post().uri("/api/game/{c}/start", character)
            .body(if (mode == null) mapOf<String, Any>() else mapOf("mode" to mode))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!! as Map<String, Any>

    // ───────────────────────── 4 인물 + job 시나리오 로드 확인 ─────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `다섯 시나리오 start 로드`() {
        for (c in listOf("joseph", "moses", "david", "jesus", "job")) {
            val body = start(c, "emotional")
            assertThat(body).`as`("character %s", c).containsEntry("character", c)
            assertThat(body["currentScene"] as Int).isEqualTo(1)
            assertThat(body["totalScenes"] as Int).isGreaterThan(0)
            assertThat(body["sessionId"]).isNotNull()
            val payload = body["scenePayload"] as Map<String, Any>
            assertThat(payload).containsEntry("sceneId", 1)
        }
    }

    // ───────────────────────── start → decide → complete 전체 흐름 ─────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `joseph start decide complete 전체흐름`() {
        val started = start("joseph", "emotional")
        val sid = UUID.fromString(started["sessionId"] as String)

        // Scene 2 저장 결정 → monologue 응답
        val decide = authed().post().uri("/api/game/joseph/{sid}/decide", sid)
            .body(mapOf("sceneId" to 2, "decision" to mapOf("value" to "save_33")))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(decide).containsEntry("sessionId", sid.toString())
            .containsEntry("previousScene", 2)
            .containsEntry("currentScene", 3)
        assertThat(decide["responseText"] as String).contains("요셉")

        // 완료 → valuePrompt
        val complete = authed().post().uri("/api/game/joseph/{sid}/complete", sid)
            .body(mapOf("finalOutcome" to "immigrant_first", "closingMessage" to "수고했어요"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(complete).containsEntry("sessionId", sid.toString())
        assertThat(complete["completedAt"]).isNotNull()
        assertThat(complete["durationSeconds"] as Int).isGreaterThanOrEqualTo(0)
        val vp = complete["valuePrompt"] as Map<String, Any>
        assertThat(vp).isNotNull()
        assertThat(vp["suggestedValueIds"] as List<*>).isNotEmpty()
    }

    @Test
    fun `start client capabilities 경로`() {
        // req.client() 비-null 경로 커버 — deviceType/capabilities 를 body 에서 직접.
        val body = authed().post().uri("/api/game/moses/start")
            .body(
                mapOf(
                    "mode" to "rational",
                    "client" to mapOf(
                        "deviceType" to "quest2",
                        "capabilities" to mapOf("handTracking" to true),
                    ),
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("character", "moses").containsEntry("appliedMode", "rational")
    }

    // ───────────────────────────────── 에러 분기 ─────────────────────────────────

    @Test
    fun `start 잘못된 character 404`() {
        try {
            authed().post().uri("/api/game/simeon/start").body(mapOf("mode" to "emotional"))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 404")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(404)
            assertThat(e.responseBodyAsString).contains("E_CHARACTER_UNKNOWN")
        }
    }

    @Test
    fun `decide 없는 세션 404`() {
        val ghost = UUID.randomUUID()
        try {
            authed().post().uri("/api/game/joseph/{sid}/decide", ghost)
                .body(mapOf("sceneId" to 2, "decision" to mapOf("value" to "save_33")))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 404")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(404)
            assertThat(e.responseBodyAsString).contains("E_SESSION_NOT_FOUND")
        }
    }

    @Test
    fun `decide character 불일치 404`() {
        // joseph 세션을 moses 로 decide → E_CHARACTER_UNKNOWN
        val started = start("joseph", "emotional")
        val sid = UUID.fromString(started["sessionId"] as String)
        try {
            authed().post().uri("/api/game/moses/{sid}/decide", sid)
                .body(mapOf("sceneId" to 2, "decision" to mapOf("value" to "save_33")))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 404")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(404)
        }
    }

    @Test
    fun `complete 두번 두번째는 409`() {
        val started = start("david", "emotional")
        val sid = UUID.fromString(started["sessionId"] as String)
        authed().post().uri("/api/game/david/{sid}/complete", sid)
            .body(mapOf("finalOutcome" to "out")).retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        try {
            authed().post().uri("/api/game/david/{sid}/complete", sid)
                .body(mapOf("finalOutcome" to "out")).retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 409")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(409)
            assertThat(e.responseBodyAsString).contains("E_SESSION_INVALID")
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `outro 위기 문구는 토큰이 아니라 실제 번호로 나간다`() {
        // 시나리오 yml 은 {{crisis_resources.default}} 토큰만 갖는다. 치환이 끊기면
        // 위기 상태의 사용자가 토큰 원문을 읽게 된다 — 낡은 번호보다 나쁜 결과다.
        for (c in listOf("elijah", "job")) {
            val started = start(c, "emotional")
            val sid = UUID.fromString(started["sessionId"] as String)

            // Scene 4 결정 → 다음 payload 가 crisis_reminder 를 가진 outro(Scene 5).
            val decide = authed().post().uri("/api/game/{c}/{sid}/decide", c, sid)
                .body(mapOf("sceneId" to 4, "decision" to mapOf("value" to "next")))
                .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!

            val payload = decide["scenePayload"] as Map<String, Any?>
            val extras = payload["extras"] as Map<String, Any?>
            assertThat(extras["crisis_reminder"].toString())
                .`as`("character %s", c)
                .doesNotContain("{{")
                .contains("109")
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `converges_to 재고 선택은 HTTP 레벨에서도 씬을 넘기지 않는다`() {
        // 저작 의도는 "재고 후 수렴" 이다. 이 계약이 엔진이 아니라 프론트에만 있으면
        // VR·API 직접 호출 클라이언트는 수렴 구간을 통째로 건너뛴다 —
        // 그래서 web 계층까지 내려와 확인한다(solomon scene3, 왕상 3:24~28).
        val started = start("solomon", "spiritual")
        val sid = UUID.fromString(started["sessionId"] as String)

        val reconsider = authed().post().uri("/api/game/solomon/{sid}/decide", sid)
            .body(mapOf("sceneId" to 3, "decision" to mapOf("value" to "first_woman")))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(reconsider)
            .containsEntry("previousScene", 3)
            .containsEntry("currentScene", 3)
        assertThat(reconsider["responseText"] as String).contains("판결을 멈추고")
        // 같은 씬을 돌려주므로 payload 도 scene 3 이어야 한다.
        assertThat(reconsider["scenePayload"] as Map<String, Any?>).containsEntry("sceneId", 3)

        // 성경 경로(sword_test)는 converges_to 가 없다 → 평소대로 Scene 4 로 진행.
        val advance = authed().post().uri("/api/game/solomon/{sid}/decide", sid)
            .body(mapOf("sceneId" to 3, "decision" to mapOf("value" to "sword_test")))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(advance)
            .containsEntry("previousScene", 3)
            .containsEntry("currentScene", 4)
    }

    @Test
    fun `미인증 start 거부`() {
        try {
            client().post().uri("/api/game/joseph/start").body(mapOf("mode" to "emotional"))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected auth failure")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        } catch (e: HttpServerErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }
}
