package github.lms.lemuel.xr.emotion.application

import github.lms.lemuel.xr.emotion.domain.Emotion
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 사용자 텍스트 → 감정 분류 + 트랙 A/B 추천 + 로그 영속 + **위기 키워드 즉시 차단**.
 *
 * 흐름:
 * 1. CrisisKeywordScanner 로 위기 키워드 즉시 검사 (LLM 호출 전)
 * 2. 등급별 분기 — docs/EMOTION-CLASSIFIER.md §3 분기표가 정본이다:
 *    - `critical` : 분류 skip. LLM 호출 자체를 안 한다. crisisLockoutRequired=true + 위기 자원.
 *    - `high`     : 분류 수행 + 위기 자원 *상단 배너*. 추천도 함께 준다.
 *    - `medium`   : 분류 수행 + *조용한* 위기 자원 카드(하단). 흐름을 끊지 않는다.
 *    - 매칭 없음   : 평소대로 AI 분류 + 추천.
 * 3. 등급과 무관하게 매칭이면 safety_alerts 에 기록한다. 운영자 Telegram 알람만 high 이상.
 *
 * **`critical` 에서 LLM 을 건너뛰는 것은 성능이 아니라 프라이버시다.** 가장 위중한 문장이
 * 외부 분류 API 로 나가지 않게 하는 설계고(EMOTION-CLASSIFIER.md §3), 그래서 `high` 와
 * 합칠 수 없다 — 배너 위치 차이가 아니라 데이터가 어디까지 가느냐의 차이다.
 *
 * `medium` 에서 분류를 계속하는 이유: 이 등급의 키워드(`마지막`·`혼자 살아`)는 일상어와
 * 구분이 안 된다. 여기서 흐름을 끊으면 위기가 아닌 사용자가 매번 벽을 맞는다. 등급이 낮다는
 * 것은 위험이 작다는 뜻이 아니라 *문자열만으로는 확신할 수 없다* 는 뜻이다(2026-08-06 결정).
 *
 * SRP — 이 use-case 는 **위기 게이트 + 분류 + 추천 오케스트레이션**만 담당한다.
 * 감정 로그 조립·영속은 [EmotionLogRecorder] 협력자에게 위임한다.
 *
 * R1 safety line 의 핵심 — 사용자가 "죽고싶어" 라고 쓰면 위기 자원이 *먼저* 와야 한다.
 * AI 분류 응답이 *대신* 와서는 안 됨.
 */
@Service
class ClassifyAndRecommendUseCase(
    private val classifier: ClassifyEmotionUseCase,
    private val recommender: EmotionRecommender,
    private val logRecorder: EmotionLogRecorder,
    private val scanner: CrisisKeywordScanner,
    private val safetyAlert: RecordSafetyAlertUseCase,
) {

    @Transactional
    fun execute(userId: UUID, text: String, preferredMode: String?): Result {
        // Layer 3 — 위기 키워드 검사 (LLM 호출 전). 기록은 등급과 무관하게 먼저 한다.
        val scan = scanner.scan(text)
        val alertResult = if (scan.matched) {
            safetyAlert.execute(userId, null, "emotion_text", scan)
        } else {
            RecordSafetyAlertUseCase.Result.notTriggered()
        }

        if (scan.severity == CrisisKeywordScanner.CRITICAL) {
            // 위기 응답 — AI 분류 응답 자리를 위기 자원이 차지. LLM 호출 없음(프라이버시).
            return Result.crisis(scan.severity, alertResult.shownResources)
        }

        val classified = classifier.classify(text)
        val emo = classified.emotion

        val topicSuggestions = recommender.trackA(emo)
        val characterSuggestions = recommender.trackB(emo)

        // docs/safety-guidelines.md §3 (PHI 비수집) — 사용자 자유 텍스트 는 분류에만 사용,
        // 영속화 금지. text 변수는 이 메서드 끝나면 GC. raw_text 컬럼은 V20260522210000 에서 DB 제거.
        val log = logRecorder.record(
            userId, emo, classified.confidence,
            preferredMode, topicSuggestions, characterSuggestions,
        )

        return Result(
            log.id, emo, classified.confidence,
            topicSuggestions, characterSuggestions,
            false, scan.severity, alertResult.shownResources,
            placementOf(scan.severity),
        )
    }

    /** 등급 → 위기 자원을 화면 어디에 두는가. 매칭이 없으면 null. */
    private fun placementOf(severity: String?): String? = when (severity) {
        CrisisKeywordScanner.CRITICAL -> Result.LOCKOUT
        CrisisKeywordScanner.HIGH -> Result.BANNER
        CrisisKeywordScanner.MEDIUM -> Result.CARD
        else -> null
    }

    data class Result(
        val emotionLogId: Long?,
        val primary: Emotion?,
        val confidence: Double,
        val trackA: List<EmotionRecommender.TopicSuggestion>,
        val trackB: List<EmotionRecommender.CharacterSuggestion>,
        /** R1 safety lockout flag — true 면 클라이언트는 *위기 자원 화면* 강제 표시. */
        val crisisLockoutRequired: Boolean,
        val crisisSeverity: String?,
        val crisisResources: List<Map<String, Any?>>,
        /**
         * 위기 자원의 표시 위치 — `lockout`(화면 강제) · `banner`(상단) · `card`(하단 조용히).
         * null 이면 위기 신호 없음. 클라이언트가 등급 문자열을 직접 해석하지 않게 하려고
         * 서버가 배치를 결정해 내려준다 — 등급 체계가 바뀌어도 클라이언트는 그대로다.
         */
        val crisisResourcePlacement: String?,
    ) {
        companion object {
            const val LOCKOUT = "lockout"
            const val BANNER = "banner"
            const val CARD = "card"

            fun crisis(severity: String?, resources: List<Map<String, Any?>>): Result =
                Result(
                    null, null, 0.0, emptyList(), emptyList(),
                    true, severity, resources, LOCKOUT,
                )
        }
    }
}
