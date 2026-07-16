package github.lms.lemuel.xr.emotion.application

import github.lms.lemuel.xr.emotion.application.port.out.EmotionLogPort
import github.lms.lemuel.xr.emotion.domain.Emotion
import github.lms.lemuel.xr.emotion.domain.EmotionLog
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * ClassifyAndRecommendUseCase 단위 테스트 — mockito-kotlin 으로 협력자를 목킹.
 *
 * 핵심 분기:
 * - 위기 키워드 매칭 → AI 분류 *건너뛰고* crisis 응답, 분류/로그 저장 안 함 (R1 safety line)
 * - 정상 → 분류 + trackA/trackB 추천 + 로그 영속 (recommendedTrack A/B 분기)
 *
 * 진짜 [EmotionRecommender] + 진짜 [EmotionLogRecorder](로그 조립 SRP) +
 * 목킹된 classifier/scanner/logs 사용. 저장은 도메인 [EmotionLog] 로 검증.
 */
class ClassifyAndRecommendUseCaseTest {

    private val recommender = EmotionRecommender()
    private val classifier: ClassifyEmotionUseCase = mock()
    private val logs: EmotionLogPort = mock()
    private val logRecorder = EmotionLogRecorder(logs)
    private val scanner: CrisisKeywordScanner = mock()
    private val safetyAlert: RecordSafetyAlertUseCase = mock()

    private val uc = ClassifyAndRecommendUseCase(classifier, recommender, logRecorder, scanner, safetyAlert)

    private val userId: UUID = UUID.randomUUID()

    // ───────────────────────── 위기 게이트 (R1 safety line) ─────────────────────────

    @Test
    fun `위기 키워드 매칭시 AI 분류 건너뛰고 crisis 응답`() {
        val scan = CrisisKeywordScanner.ScanResult(true, "suicide_intent", "high", "hash")
        whenever(scanner.scan(any())).thenReturn(scan)
        val resources: List<Map<String, Any?>> = listOf(mapOf("name" to "1393"))
        whenever(safetyAlert.execute(eq(userId), isNull(), eq("emotion_text"), eq(scan)))
            .thenReturn(RecordSafetyAlertUseCase.Result(true, 42L, resources))

        val r = uc.execute(userId, "죽고 싶어요", "emotional")

        assertThat(r.crisisLockoutRequired).isTrue()
        assertThat(r.crisisSeverity).isEqualTo("high")
        assertThat(r.crisisResources).isEqualTo(resources)
        assertThat(r.primary).isNull()
        assertThat(r.trackA).isEmpty()
        // AI 분류·로그 저장 모두 건너뜀 — 위기 자원이 *먼저* 온다.
        verify(classifier, never()).classify(any())
        verify(logs, never()).save(any())
    }

    // ───────────────────────── 정상 흐름 ─────────────────────────

    @Test
    fun `정상 ANXIOUS 분류시 trackA trackB 추천 및 로그저장`() {
        whenever(scanner.scan(any())).thenReturn(CrisisKeywordScanner.ScanResult.none())
        whenever(classifier.classify("불안해요"))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.ANXIOUS, 0.87))
        // save 시 id 부여 흉내
        whenever(logs.save(any())).thenAnswer { it.getArgument(0) }

        val r = uc.execute(userId, "불안해요", "emotional")

        assertThat(r.crisisLockoutRequired).isFalse()
        assertThat(r.primary).isEqualTo(Emotion.ANXIOUS)
        assertThat(r.confidence).isEqualTo(0.87)
        assertThat(r.trackA).isNotEmpty()
        assertThat(r.trackB).extracting<String> { it.character }
            .contains("MOSES")

        val saved = argumentCaptor<EmotionLog>()
        verify(logs).save(saved.capture())
        val log = saved.firstValue
        assertThat(log.userId).isEqualTo(userId)
        assertThat(log.classifiedEmotion).isEqualTo("ANXIOUS")
        assertThat(log.chosenDimension).isEqualTo("emotional")
        // ANXIOUS 는 trackA topic 이 존재 → recommendedTrack "A", recommendedContent "topic:..."
        assertThat(log.recommendedTrack).isEqualTo("A")
        assertThat(log.recommendedContent).startsWith("topic:")
        assertThat(log.confidence!!.toDouble()).isEqualTo(0.87)
        verify(safetyAlert, never()).execute(any(), any(), any(), any())
    }

    @Test
    fun `정상 trackA 비어있으면 recommendedTrack B 이고 mission content`() {
        // classify 결과가 어떤 emotion 이든 recommender 가 빈 trackA 를 주는 상황을 흉내내기 위해
        // recommender 를 목킹해 trackA=[] / trackB=[mission] 케이스를 직접 만든다.
        val mockRec: EmotionRecommender = mock()
        val mission = EmotionRecommender.CharacterSuggestion("JOSEPH", 1, "이유", 0.9)
        whenever(mockRec.trackA(any())).thenReturn(emptyList())
        whenever(mockRec.trackB(any())).thenReturn(listOf(mission))

        val localUc = ClassifyAndRecommendUseCase(classifier, mockRec, logRecorder, scanner, safetyAlert)
        whenever(scanner.scan(any())).thenReturn(CrisisKeywordScanner.ScanResult.none())
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.CONFUSED, 0.6))
        whenever(logs.save(any())).thenAnswer { it.getArgument(0) }

        val r = localUc.execute(userId, "혼란", null)

        assertThat(r.trackA).isEmpty()
        val saved = argumentCaptor<EmotionLog>()
        verify(logs).save(saved.capture())
        assertThat(saved.firstValue.recommendedTrack).isEqualTo("B")
        assertThat(saved.firstValue.recommendedContent).isEqualTo("mission:joseph")
        assertThat(saved.firstValue.chosenDimension).isNull()
    }

    @Test
    fun `정상 trackA trackB 모두 비면 recommendedContent null`() {
        val mockRec: EmotionRecommender = mock()
        whenever(mockRec.trackA(any())).thenReturn(emptyList())
        whenever(mockRec.trackB(any())).thenReturn(emptyList())

        val localUc = ClassifyAndRecommendUseCase(classifier, mockRec, logRecorder, scanner, safetyAlert)
        whenever(scanner.scan(any())).thenReturn(CrisisKeywordScanner.ScanResult.none())
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.SAD, 0.5))
        whenever(logs.save(any())).thenAnswer { it.getArgument(0) }

        val r = localUc.execute(userId, "슬픔", "spiritual")

        assertThat(r.crisisLockoutRequired).isFalse()
        val saved = argumentCaptor<EmotionLog>()
        verify(logs).save(saved.capture())
        assertThat(saved.firstValue.recommendedTrack).isEqualTo("B")
        assertThat(saved.firstValue.recommendedContent).isNull()
    }
}
