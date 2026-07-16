package github.lms.lemuel.xr

import github.lms.lemuel.xr.common.chatops.TelegramChatOps
import github.lms.lemuel.xr.emotion.application.ClassifyEmotionUseCase
import github.lms.lemuel.xr.emotion.domain.Emotion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * emotion/adapter/in/web/EmotionController 통합 테스트.
 *
 * AI 사이드카 분류([ClassifyEmotionUseCase]) 는 [MockitoBean] 으로 목킹 — 실제 LLM
 * 호출 없음. TelegramChatOps 도 목킹(테스트 프로필에서 chatops disabled 이지만 방어적).
 * ContentWebIT 의 게스트 발급 → disclaimer 동의 인증 패턴 재사용.
 *
 * 커버 분기:
 * - 정상 텍스트 → 분류 + trackA/trackB 추천 응답
 * - 위기 키워드("죽고 싶어") → crisisLockout 응답, 분류/추천은 모두 null (R1 safety line)
 * - 인증 없으면 401/451, 빈 텍스트 검증 실패 400
 */
class EmotionWebIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    @MockitoBean
    lateinit var classifier: ClassifyEmotionUseCase

    @MockitoBean
    lateinit var chatOps: TelegramChatOps

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
                    "deviceFingerprint" to "emotion-web-it-" + System.nanoTime(),
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
    fun `정상 텍스트 분류후 추천 응답`() {
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.ANXIOUS, 0.88))

        val body = authed().post().uri("/api/emotion/classify")
            .body(
                mapOf(
                    "text" to "오늘 좀 불안했어요",
                    "context" to mapOf("preferredMode" to "emotional"),
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!

        assertThat(body["crisisLockout"]).isNull()
        val primary = body["primary"] as Map<String, Any>
        assertThat(primary).containsEntry("emotion", "ANXIOUS")
        assertThat((primary["confidence"] as Number).toDouble()).isEqualTo(0.88)
        val recs = body["recommendations"] as Map<String, Any>
        assertThat(recs["trackA"]).isNotNull()
        assertThat(recs["trackB"].toString()).contains("MOSES")
        assertThat(body["emotionLogId"]).isNotNull()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `위기 키워드 crisisLockout 응답 분류는 null`() {
        // 실제 CrisisKeywordScanner regex 매칭 — "죽고 싶" 은 기본 regex 에 포함.
        // 위기 게이트가 AI 분류보다 *먼저* 동작해야 한다 (classifier 는 호출되지 않음).
        val body = authed().post().uri("/api/emotion/classify")
            .body(mapOf("text" to "이제 그만 죽고 싶어요"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!

        assertThat(body["primary"]).isNull()
        assertThat(body["recommendations"]).isNull()
        val lockout = body["crisisLockout"] as Map<String, Any>
        assertThat(lockout).isNotNull()
        assertThat(lockout).containsEntry("required", true)
        assertThat(lockout).containsEntry("severity", "high")
        assertThat(lockout["gentleMessage"].toString()).contains("1393")
    }

    @Test
    fun `인증 없으면 401 또는 451`() {
        try {
            client().post().uri("/api/emotion/classify")
                .body(mapOf("text" to "hi"))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected auth failure")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }

    @Test
    fun `빈 텍스트는 400 검증실패`() {
        try {
            authed().post().uri("/api/emotion/classify")
                .body(mapOf("text" to ""))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }
}
