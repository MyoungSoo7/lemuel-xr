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
 * - critical("죽고 싶어") → crisisLockout 응답, 분류/추천은 모두 null (R1 safety line)
 * - high/medium → 분류·추천은 그대로 오고 crisisSupport 만 배너/카드로 덧붙는다
 * - 인증 없으면 401/451, 빈 텍스트 검증 실패 400
 *
 * 등급 분기는 단위 테스트가 fixture regex 로 이미 덮는다. 여기서 다시 보는 이유는
 * *application.yml 의 실제 regex* 가 명명 그룹까지 제대로 실려 오는지는 스프링을 띄워야만
 * 확인되기 때문이다. 그룹 이름 하나가 오타 나면 전부 crisis_unclassified→critical 로 떨어져
 * 단위 테스트는 다 통과하는데 운영에서는 사별 문장까지 lockout 이 된다.
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
        // 3단 등급(2026-08-06) 이전에는 severity 가 무엇이 걸리든 "high" 하드코딩이었다.
        // 자살 의도는 등급 도입 이후 critical — 동작(lockout)은 그대로고 이름만 사실과 맞춰졌다.
        assertThat(lockout).containsEntry("severity", "critical")
        assertThat(lockout["gentleMessage"].toString()).contains("109")
        // critical 은 crisisSupport 를 쓰지 않는다 — 자원은 lockout 안에 이미 들어있다.
        assertThat(body["crisisSupport"]).isNull()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `high 사별 표현은 흐름을 끊지 않고 배너만 붙는다`() {
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.SAD, 0.7))

        val body = authed().post().uri("/api/emotion/classify")
            .body(mapOf("text" to "그 사람이랑 같이 묻히고 싶어요"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!

        assertThat(body["crisisLockout"]).isNull()
        val support = body["crisisSupport"] as Map<String, Any>
        assertThat(support).containsEntry("severity", "high")
        assertThat(support).containsEntry("placement", "banner")
        assertThat(support["resources"].toString()).contains("109")
        // 벽이 아니라 곁 — 분류와 추천은 정상적으로 온다.
        assertThat((body["primary"] as Map<String, Any>)).containsEntry("emotion", "SAD")
        assertThat(body["recommendations"]).isNotNull()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `medium 사별 표현은 하단 카드로만 붙는다`() {
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.LONELY, 0.6))

        val body = authed().post().uri("/api/emotion/classify")
            .body(mapOf("text" to "먼저 간 아내 곁으로 가고 싶어요"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!

        assertThat(body["crisisLockout"]).isNull()
        val support = body["crisisSupport"] as Map<String, Any>
        assertThat(support).containsEntry("severity", "medium")
        assertThat(support).containsEntry("placement", "card")
        assertThat(body["recommendations"]).isNotNull()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `평범한 문장에는 위기 필드가 아예 없다`() {
        // 등급이 생기면서 새로 만들어진 실패 모드 — 아무 문장에나 조용한 카드가 붙는 것.
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.GRATEFUL, 0.9))

        val body = authed().post().uri("/api/emotion/classify")
            .body(mapOf("text" to "오늘은 오랜만에 산책을 했어요"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!

        assertThat(body["crisisLockout"]).isNull()
        assertThat(body["crisisSupport"]).isNull()
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
