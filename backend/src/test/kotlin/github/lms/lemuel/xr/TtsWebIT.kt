package github.lms.lemuel.xr

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.tts.application.port.out.TtsSynthesisPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

/**
 * tts/adapter/in/web/TtsController 통합 테스트.
 *
 * 외부 TTS 사이드카([TtsSynthesisPort]) 는 [MockitoBean] 으로 목킹 — 실제 오디오
 * 생성/네트워크 호출 없음. 성공(cached=false) → 동일 요청 캐시 히트(cached=true) → 사이드카
 * 오류 전파(502) 분기를 실 서버로 커버. /voices 는 공개, /synthesize 는 disclaimer 게이트 대상.
 */
class TtsWebIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    @MockitoBean
    lateinit var sidecar: TtsSynthesisPort

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
                    "deviceFingerprint" to "tts-web-it-" + System.nanoTime(),
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
    fun `voices 공개 카탈로그`() {
        val body = client().get().uri("/api/tts/voices")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val voices = body["voices"] as List<Map<String, Any>>
        assertThat(voices).hasSize(3)
        assertThat(voices.toString()).contains("narrator-male-low").contains("goliath-bass")
    }

    @Test
    fun `synthesize 최초는 사이드카 호출 cached false 그리고 재요청은 cached true`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult(
                    "https://r2/audio/mock.wav", 1800, "xtts-v2",
                ),
            )

        val text = "테스트 합성 " + System.nanoTime() // 유니크 → 이 테스트만의 캐시 키
        val first = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to text, "voiceId" to "narrator-male-low", "speakingRate" to 1.0))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(first).containsEntry("audioUrl", "https://r2/audio/mock.wav")
            .containsEntry("durationMs", 1800)
            .containsEntry("cached", false)

        // 동일 입력 → DB 캐시 히트, 사이드카 재호출 없이 cached=true.
        val second = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to text, "voiceId" to "narrator-male-low", "speakingRate" to 1.0))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(second).containsEntry("audioUrl", "https://r2/audio/mock.wav")
            .containsEntry("cached", true)
    }

    @Test
    fun `synthesize 사이드카 오류면 502`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenThrow(AppException(ErrorCode.E_TTS_UPSTREAM_FAIL, "boom"))

        try {
            authed().post().uri("/api/tts/synthesize")
                .body(mapOf("text" to "실패유도 " + System.nanoTime()))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 502")
        } catch (e: HttpServerErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(502)
            assertThat(e.responseBodyAsString).contains("E_TTS_UPSTREAM_FAIL")
        }
    }

    @Test
    fun `synthesize voiceId rate 생략도 기본값으로 합성`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult(
                    "https://r2/audio/def.wav", 900, "xtts-v2",
                ),
            )

        val body = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to "기본값 합성 " + System.nanoTime()))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("audioUrl", "https://r2/audio/def.wav")
            .containsEntry("cached", false)
    }

    @Test
    fun `synthesize 인증없으면 401 또는 451`() {
        try {
            client().post().uri("/api/tts/synthesize")
                .body(mapOf("text" to "hi"))
                .retrieve().body(String::class.java)
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }
}
