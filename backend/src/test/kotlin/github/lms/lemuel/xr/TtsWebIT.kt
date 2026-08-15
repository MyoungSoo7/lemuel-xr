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
import org.springframework.web.client.RestClient

/**
 * tts/adapter/in/web/TtsController 통합 테스트 — 비동기(202 + 폴링) 계약 기준.
 *
 * 외부 TTS 사이드카([TtsSynthesisPort]) 는 [MockitoBean] 으로 목킹 — 실제 오디오
 * 생성/네트워크 호출 없음. 다만 **큐 어댑터는 진짜**라 합성은 별도 워커 스레드에서 돈다.
 * 그래서 이 테스트는 즉시성을 가정하지 않고 [pollUntilSettled] 로 기다린다.
 *
 * 커버 범위: 캐시 미스 202 → 폴링 ready → 재요청 200 cached / 사이드카 오류가 failed 로
 * 드러나는 경로 / 모르는 jobId 404 / 상한 초과 400. /voices 는 공개, /synthesize 는
 * disclaimer 게이트 대상.
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

    /** 202 로 받은 jobId 를 ready/failed 가 될 때까지 폴링한다. */
    private fun pollUntilSettled(jobId: String): Map<String, Any?> {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var last: Map<String, Any?> = mapOf()
        while (System.currentTimeMillis() < deadline) {
            last = authed().get().uri("/api/tts/jobs/$jobId")
                .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
            if (last["status"] != "pending") return last
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return Assertions.fail("job $jobId 가 ${POLL_TIMEOUT_MS}ms 안에 안 끝남 — 마지막 응답: $last")
    }

    /**
     * 2026-08-15 프로덕션 사고의 회귀 테스트.
     *
     * 엔진을 Gemini 로 바꾸자 사이드카가 돌려주는 engine 값이 `xtts-v2`(7자)에서
     * `gemini-3.1-flash-tts-preview`(28자)가 됐는데, `tts_cache.engine` 이
     * `VARCHAR(20)` 이라 **저장이 통째로 터졌다**(`value too long for type character
     * varying(20)`). 사이드카는 200 으로 오디오를 잘 만들어 돌려주는데 백엔드가 그걸
     * 못 써서 매번 FAILED — 사용자에게는 "나레이션이 그냥 안 나온다"로만 보였다.
     *
     * 기존 테스트가 이걸 놓친 이유는 하나다. **목이 짧은 이름을 돌려주고 있었다.**
     * 컬럼 폭은 진짜 DB 에 써봐야만 드러나므로(Flyway + Postgres 컨테이너가 도는
     * 이 IT 가 그 자리다), 여기서는 실제 모델 이름을 그대로 쓴다.
     */
    @Test
    fun `벤더 모델 이름처럼 긴 engine 값도 저장된다`() {
        val longEngine = "gemini-3.1-flash-tts-preview" // 28자 — 옛 VARCHAR(20) 을 넘긴다
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult("https://r2/audio/gem.wav", 17000, longEngine),
            )

        val text = "긴 엔진 이름 " + System.nanoTime()
        val res = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to text, "voiceId" to "narrator-male-low", "speakingRate" to 1.0))
            .retrieve().toEntity(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        assertThat(res.statusCode.value()).isEqualTo(202)

        // 저장이 터지면 여기서 ready 가 아니라 failed 가 온다.
        val settled = pollUntilSettled(res.body!!["jobId"] as String)
        assertThat(settled).containsEntry("status", "ready")
            .containsEntry("audioUrl", "https://r2/audio/gem.wav")
    }

    @Test
    fun `synthesize 최초는 202 pending 폴링하면 ready 그리고 재요청은 즉시 cached true`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult(
                    "https://r2/audio/mock.wav", 1800, "xtts-v2",
                ),
            )

        val text = "테스트 합성 " + System.nanoTime() // 유니크 → 이 테스트만의 캐시 키
        val first = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to text, "voiceId" to "narrator-male-low", "speakingRate" to 1.0))
            .retrieve().toEntity(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})

        // 캐시에 없으면 기다리게 하지 않고 즉시 202 + jobId 를 준다.
        assertThat(first.statusCode.value()).isEqualTo(202)
        assertThat(first.body).containsEntry("status", "pending")
        // null 필드는 직렬화에서 아예 빠진다 — 키 유무가 아니라 값으로 본다.
        assertThat(first.body!!["audioUrl"]).isNull()
        val jobId = first.body!!["jobId"] as String
        assertThat(jobId).isNotBlank()

        val settled = pollUntilSettled(jobId)
        assertThat(settled).containsEntry("status", "ready")
            .containsEntry("audioUrl", "https://r2/audio/mock.wav")
            .containsEntry("durationMs", 1800)

        // 동일 입력 → DB 캐시 히트, 이번엔 폴링 없이 200 으로 바로 온다.
        val second = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to text, "voiceId" to "narrator-male-low", "speakingRate" to 1.0))
            .retrieve().toEntity(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        assertThat(second.statusCode.value()).isEqualTo(200)
        assertThat(second.body).containsEntry("status", "ready")
            .containsEntry("audioUrl", "https://r2/audio/mock.wav")
            .containsEntry("cached", true)
    }

    @Test
    fun `사이드카 오류는 502 가 아니라 폴링에서 failed 로 드러난다`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenThrow(AppException(ErrorCode.E_TTS_UPSTREAM_FAIL, "boom"))

        // 비동기로 바뀌면서 제출 자체는 성공한다 — 합성 실패를 HTTP 상태로 알 방법이 없다.
        val submitted = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to "실패유도 " + System.nanoTime()))
            .retrieve().toEntity(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        assertThat(submitted.statusCode.value()).isEqualTo(202)

        val settled = pollUntilSettled(submitted.body!!["jobId"] as String)
        assertThat(settled).containsEntry("status", "failed")
        assertThat(settled["audioUrl"]).isNull()
    }

    @Test
    fun `모르는 jobId 는 404`() {
        try {
            authed().get().uri("/api/tts/jobs/존재하지않는키")
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 404")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(404)
        }
    }

    @Test
    fun `text 가 상한 500자를 넘으면 400`() {
        try {
            authed().post().uri("/api/tts/synthesize")
                .body(mapOf("text" to "가".repeat(501)))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `지원하지 않는 언어는 202 가 아니라 400 이다`() {
        // 통과시키면 사이드카의 400 이 워커 스레드 안에서 터지고, 사용자에게는 한참 폴링한
        // 끝의 이유 없는 failed 로만 보인다. 요청 시점에 끊어야 원인이 드러난다.
        listOf("es", "ja", "korean").forEach { bad ->
            try {
                authed().post().uri("/api/tts/synthesize")
                    .body(mapOf("text" to "hola", "language" to bad))
                    .retrieve().body(String::class.java)
                Assertions.fail<Any>("expected 400 for language=$bad")
            } catch (e: HttpClientErrorException) {
                assertThat(e.statusCode.value()).isEqualTo(400)
            }
        }
    }

    @Test
    fun `en 요청은 사이드카까지 en 으로 간다`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult(
                    "https://r2/audio/en.wav", 1100, "xtts-v2", "en",
                ),
            )

        val submitted = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to "David and Goliath " + System.nanoTime(), "language" to "en"))
            .retrieve().toEntity(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        assertThat(submitted.statusCode.value()).isEqualTo(202)

        val settled = pollUntilSettled(submitted.body!!["jobId"] as String)
        assertThat(settled).containsEntry("status", "ready")

        val lang = org.mockito.kotlin.argumentCaptor<String>()
        org.mockito.kotlin.verify(sidecar)
            .synthesize(any(), anyOrNull(), anyOrNull(), lang.capture())
        assertThat(lang.firstValue).isEqualTo("en")
    }

    @Test
    fun `synthesize voiceId rate 생략도 기본값으로 합성`() {
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult(
                    "https://r2/audio/def.wav", 900, "xtts-v2",
                ),
            )

        val submitted = authed().post().uri("/api/tts/synthesize")
            .body(mapOf("text" to "기본값 합성 " + System.nanoTime()))
            .retrieve().toEntity(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
        assertThat(submitted.statusCode.value()).isEqualTo(202)

        val settled = pollUntilSettled(submitted.body!!["jobId"] as String)
        assertThat(settled).containsEntry("status", "ready")
            .containsEntry("audioUrl", "https://r2/audio/def.wav")
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

    companion object {
        /**
         * 사이드카가 목킹돼 있어 실제 합성은 즉시 끝난다 — 이 값은 합성 시간이 아니라
         * *워커 스레드로 넘어갔다 오는 시간* 에 대한 여유다. CI 가 느릴 때를 감안해 넉넉히 준다.
         */
        private const val POLL_TIMEOUT_MS: Long = 15_000

        private const val POLL_INTERVAL_MS: Long = 100
    }
}
