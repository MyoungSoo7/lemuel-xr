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
 * 1. CrisisKeywordScanner 로 자살·자해 키워드 즉시 검사 (LLM 호출 전)
 * 2. 매칭 시: safety_alerts 기록 + Telegram 알람 + 응답에 crisisLockoutRequired=true + 위기 자원
 * 3. 매칭 안 됨: 평소대로 AI 분류 + 추천
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
        // Layer 3 — 위기 키워드 즉시 차단 (LLM 호출 전)
        val scan = scanner.scan(text)
        if (scan.matched) {
            val alertResult = safetyAlert.execute(userId, null, "emotion_text", scan)
            // 위기 응답 — AI 분류 응답 자리를 위기 자원이 차지.
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
            false, null, emptyList(),
        )
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
    ) {
        companion object {
            fun crisis(severity: String?, resources: List<Map<String, Any?>>): Result =
                Result(
                    null, null, 0.0, emptyList(), emptyList(),
                    true, severity, resources,
                )
        }
    }
}
