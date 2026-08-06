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
 * 핵심 분기 (docs/EMOTION-CLASSIFIER.md §3 분기표가 정본):
 * - `critical` → AI 분류 *건너뛰고* crisis 응답, 분류/로그 저장 안 함 (R1 safety line)
 * - `high`/`medium` → 분류·추천은 그대로 하고 위기 자원을 배너/카드로 *덧붙인다*
 * - 정상 → 분류 + trackA/trackB 추천 + 로그 영속 (recommendedTrack A/B 분기)
 *
 * high/medium 이 흐름을 끊지 않는 이유는 등급이 "덜 위험하다" 를 뜻해서가 아니라
 * "이 문자열만으로는 확신할 수 없다" 를 뜻하기 때문이다. 확신 없는 판정으로 매번 벽을 세우면
 * 위기가 아닌 사용자가 앱을 떠나고, 정작 위기일 때 쓸 자리도 사라진다.
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
    fun `critical 매칭시 AI 분류 건너뛰고 crisis 응답`() {
        val scan = CrisisKeywordScanner.ScanResult(true, "suicide_intent", "critical", "hash")
        whenever(scanner.scan(any())).thenReturn(scan)
        val resources: List<Map<String, Any?>> = listOf(mapOf("name" to "109"))
        whenever(safetyAlert.execute(eq(userId), isNull(), eq("emotion_text"), eq(scan)))
            .thenReturn(RecordSafetyAlertUseCase.Result(true, 42L, resources))

        val r = uc.execute(userId, "죽고 싶어요", "emotional")

        assertThat(r.crisisLockoutRequired).isTrue()
        assertThat(r.crisisSeverity).isEqualTo("critical")
        assertThat(r.crisisResources).isEqualTo(resources)
        assertThat(r.crisisResourcePlacement).isEqualTo("lockout")
        assertThat(r.primary).isNull()
        assertThat(r.trackA).isEmpty()
        // AI 분류·로그 저장 모두 건너뜀 — 위기 자원이 *먼저* 온다.
        // classify 를 안 부르는 것은 성능이 아니라 프라이버시다(EMOTION-CLASSIFIER.md §3):
        // 가장 위중한 문장이 외부 분류 API 로 나가지 않는다.
        verify(classifier, never()).classify(any())
        verify(logs, never()).save(any())
    }

    @Test
    fun `high 매칭은 분류를 계속하고 위기 자원을 상단 배너로 함께 준다`() {
        val scan = CrisisKeywordScanner.ScanResult(true, "bereavement_companion_death", "high", "hash")
        whenever(scanner.scan(any())).thenReturn(scan)
        val resources: List<Map<String, Any?>> = listOf(mapOf("name" to "109"))
        whenever(safetyAlert.execute(eq(userId), isNull(), eq("emotion_text"), eq(scan)))
            .thenReturn(RecordSafetyAlertUseCase.Result(true, 7L, resources))
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.SAD, 0.8))
        whenever(logs.save(any())).thenAnswer { it.getArgument(0) }

        val r = uc.execute(userId, "같이 묻히고 싶다", "emotional")

        assertThat(r.crisisLockoutRequired).isFalse()
        assertThat(r.crisisSeverity).isEqualTo("high")
        assertThat(r.crisisResourcePlacement).isEqualTo("banner")
        assertThat(r.crisisResources).isEqualTo(resources)
        // 흐름을 끊지 않는다 — 분류와 추천은 정상적으로 온다.
        assertThat(r.primary).isEqualTo(Emotion.SAD)
        verify(classifier).classify(any())
        verify(logs).save(any())
    }

    @Test
    fun `medium 매칭은 조용한 하단 카드로만 붙는다`() {
        val scan = CrisisKeywordScanner.ScanResult(true, "bereavement_longing", "medium", "hash")
        whenever(scanner.scan(any())).thenReturn(scan)
        val resources: List<Map<String, Any?>> = listOf(mapOf("name" to "109"))
        whenever(safetyAlert.execute(eq(userId), isNull(), eq("emotion_text"), eq(scan)))
            .thenReturn(RecordSafetyAlertUseCase.Result(true, 8L, resources))
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.LONELY, 0.7))
        whenever(logs.save(any())).thenAnswer { it.getArgument(0) }

        val r = uc.execute(userId, "그 사람 곁으로 가고 싶어", null)

        assertThat(r.crisisLockoutRequired).isFalse()
        assertThat(r.crisisResourcePlacement).isEqualTo("card")
        assertThat(r.primary).isEqualTo(Emotion.LONELY)
    }

    @Test
    fun `등급이 낮아도 safety_alerts 기록은 건너뛰지 않는다`() {
        // medium 을 기록에서 빼면 사후에 '놓친 신호' 를 셀 수 없어진다.
        // 조용한 표시와 조용한 미기록은 다른 것이다.
        val scan = CrisisKeywordScanner.ScanResult(true, "risk_signal", "medium", "hash")
        whenever(scanner.scan(any())).thenReturn(scan)
        whenever(safetyAlert.execute(eq(userId), isNull(), eq("emotion_text"), eq(scan)))
            .thenReturn(RecordSafetyAlertUseCase.Result(true, 9L, emptyList()))
        whenever(classifier.classify(any()))
            .thenReturn(ClassifyEmotionUseCase.Result(Emotion.CONFUSED, 0.5))
        whenever(logs.save(any())).thenAnswer { it.getArgument(0) }

        uc.execute(userId, "마지막 편지를 써 뒀어요", null)

        verify(safetyAlert).execute(eq(userId), isNull(), eq("emotion_text"), eq(scan))
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
        assertThat(r.crisisResourcePlacement).isNull()
        assertThat(r.crisisResources).isEmpty()
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
