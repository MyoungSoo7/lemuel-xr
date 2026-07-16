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
 * 여러 저커버 컨트롤러 통합 테스트 — asset(/api/config) · scripture(range/search) ·
 * analytics(/api/analytics/event) · safety(game session exit) · recovery(/api/users/me/recovery).
 *
 * ContentWebIT/GameWebIT 의 게스트 발급 → disclaimer 동의 패턴 재사용.
 */
class MiscEndpointsWebIT : IntegrationTestBase() {

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
                    "deviceFingerprint" to "misc-web-it-" + System.nanoTime(),
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

    // ─────────────────────────── asset — /api/config/input-mapping ───────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `inputMapping quest3 controller grip`() {
        val body = client().get().uri("/api/config/input-mapping?device=Quest3")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val grab = body["GRAB"] as Map<String, Any>
        assertThat(grab).containsEntry("source", "controller").containsEntry("binding", "grip")
        assertThat(grab).containsKey("fallback")
        assertThat(body["POINT_AT"] as Map<String, Any>).containsEntry("binding", "raycast")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `inputMapping visionpro hand pinch`() {
        val body = client().get().uri("/api/config/input-mapping?device=visionpro")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body["GRAB"] as Map<String, Any>).containsEntry("source", "hand")
        assertThat(body["POINT_AT"] as Map<String, Any>).containsEntry("source", "eye+hand")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `inputMapping galaxyxr eye dwell`() {
        val body = client().get().uri("/api/config/input-mapping?device=galaxyxr")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body["GAZE_DURATION"] as Map<String, Any>).containsEntry("source", "eye")
    }

    @Test
    fun `inputMapping 미지디바이스 400`() {
        try {
            client().get().uri("/api/config/input-mapping?device=hololens")
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }

    // ─────────────────────────── asset — /api/config/asset-manifest ───────────────────────────

    @Test
    fun `assetManifest joseph web scene1 seed`() {
        // AssetManifestSeeder 가 classpath manifests/joseph/web/scene1-v1.0.0.json 을 시드.
        val body = client().get()
            .uri("/api/config/asset-manifest?mission=joseph&device=web&scene=1")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("missionId", "joseph").containsEntry("deviceType", "web")
        assertThat(body["version"]).isEqualTo("1.0.0")
        assertThat(body["manifest"]).isNotNull()
    }

    @Test
    fun `assetManifest galaxyxr scene지정`() {
        val body = client().get()
            .uri("/api/config/asset-manifest?mission=joseph&device=galaxyxr&scene=2")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("missionId", "joseph").containsEntry("deviceType", "galaxyxr")
        assertThat(body["sceneNumber"]).isEqualTo(2)
    }

    @Test
    fun `assetManifest scene미지정 다중매칭도 200 한건반환`() {
        // 회귀: scene 파라미터 없으면 여러 씬 manifest 가 매칭돼 이전엔 NonUniqueResultException→500.
        // Limit.of(1) 로 version 최신 1건만 취해 200 반환해야 한다.
        val body = client().get()
            .uri("/api/config/asset-manifest?mission=joseph&device=web")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("missionId", "joseph").containsEntry("deviceType", "web")
        assertThat(body["manifest"]).isNotNull()
    }

    @Test
    fun `assetManifest 미지미션 400`() {
        try {
            client().get().uri("/api/config/asset-manifest?mission=nobody&device=web")
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 4xx")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }

    // ─────────────────────────── scripture — range / search ───────────────────────────

    @Test
    fun `scripture range 창세기41 25 53`() {
        val body = client().get()
            .uri("/api/scripture/range?book=gen&chapter=41&from=25&to=53")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("book", "gen").containsEntry("chapter", 41)
        val passages = body["passages"] as List<*>
        assertThat(passages).isNotEmpty()
    }

    @Test
    fun `scripture search 키워드 하나님`() {
        val body = client().post().uri("/api/scripture/search")
            .body(mapOf("query" to "하나님", "limit" to 3))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("query", "하나님")
        val matches = body["matches"] as List<*>
        assertThat(matches).isNotEmpty()
        assertThat(matches.size).isLessThanOrEqualTo(3)
    }

    @Test
    fun `scripture search limit null 기본5`() {
        val body = client().post().uri("/api/scripture/search")
            .body(mapOf("query" to "요셉"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("query", "요셉")
    }

    @Test
    fun `scripture byRef 미존재 404`() {
        try {
            client().get().uri("/api/scripture/gen-99:99").retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 404")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(404)
        }
    }

    // ─────────────────────────── analytics — /api/analytics/event ───────────────────────────

    @Test
    fun `analytics event 배치 수락`() {
        val body = authed().post().uri("/api/analytics/event")
            .body(
                mapOf(
                    "sessionId" to UUID.randomUUID().toString(),
                    "events" to listOf(
                        mapOf("t" to "gaze", "ms" to 1200),
                        mapOf("t" to "grab", "ms" to 400),
                    ),
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat((body["accepted"] as Number).toInt()).isEqualTo(2)
    }

    @Test
    fun `analytics event 빈이벤트 0수락`() {
        val body = authed().post().uri("/api/analytics/event")
            .body(mapOf("sessionId" to UUID.randomUUID().toString()))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat((body["accepted"] as Number).toInt()).isZero()
    }

    @Test
    fun `analytics event 미인증 401`() {
        try {
            client().post().uri("/api/analytics/event")
                .body(mapOf("events" to listOf<Any>()))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 4xx")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }

    // ─────────────────────────── safety — game session emergency exit ───────────────────────────

    @Test
    fun `safety gameSession exit softLanding`() {
        // 1) 게임 세션 시작 → sessionId 확보.
        val start = authed().post().uri("/api/game/joseph/start")
            .body(mapOf("mode" to "emotional"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val sid = start["sessionId"] as String
        assertThat(sid).isNotBlank()

        // 2) emergency exit.
        val exit = authed().post().uri("/api/game/sessions/{sid}/exit", sid)
            .body(mapOf("reason" to "overwhelmed", "atSceneId" to 1))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(exit).containsEntry("sessionId", sid)
        assertThat(exit["gentleMessage"] as String).contains("시편 23편")
        assertThat(exit["exitedAt"]).isNotNull()
    }

    @Test
    fun `safety gameSession exit reason없이 기본태그`() {
        val start = authed().post().uri("/api/game/moses/start")
            .body(mapOf("mode" to "emotional"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val sid = start["sessionId"] as String

        val exit = authed().post().uri("/api/game/sessions/{sid}/exit", sid)
            .body(mapOf<String, Any>())
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(exit).containsEntry("sessionId", sid)
    }

    // ─────────────────────────── recovery — /api/users/me/recovery ───────────────────────────

    @Test
    fun `recovery 최근30일 빈결과`() {
        // 시드된 metric 없어도 200 + 빈 리스트.
        val body = authed().get().uri("/api/users/me/recovery")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body["items"]).isNotNull()
        assertThat(body["items"] as List<*>).isEmpty()
    }

    @Test
    fun `recovery days파라미터 지정`() {
        val body = authed().get().uri("/api/users/me/recovery?days=7")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body["items"] as List<*>).isNotNull()
    }

    @Test
    fun `recovery 미인증 401`() {
        try {
            client().get().uri("/api/users/me/recovery").retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 4xx")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        } catch (e: HttpServerErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }
}
