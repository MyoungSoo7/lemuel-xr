package github.lms.lemuel.xr.emotion.application

import github.lms.lemuel.xr.emotion.application.port.out.EmotionLogPort
import github.lms.lemuel.xr.emotion.domain.Emotion
import github.lms.lemuel.xr.emotion.domain.EmotionLog
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 감정 로그 **조립 + 영속** 협력자 (SRP) — [ClassifyAndRecommendUseCase] 에서 분리.
 *
 * use-case 는 위기 게이트 + 분류 + 추천 오케스트레이션에 집중하고, 이 컴포넌트는
 * 도메인 [EmotionLog] 조립(recommendedTrack/recommendedContent 결정 포함) 과
 * [EmotionLogPort] 를 통한 저장만 책임진다.
 *
 * docs/safety-guidelines.md §3 (PHI 비수집) — 사용자 자유 텍스트는 이 경로에 들어오지 않는다.
 * 분류 결과와 추천 메타데이터만 영속화한다.
 */
@Component
class EmotionLogRecorder(
    private val logs: EmotionLogPort,
) {

    /**
     * 분류 결과 + 추천을 감정 로그로 조립해 저장하고, 저장된(=id 부여된) 로그를 반환한다.
     *
     * @param userId               사용자 id
     * @param emotion              분류된 감정
     * @param confidence           분류 신뢰도
     * @param preferredMode        선택 차원 (nullable)
     * @param topicSuggestions     Track A 추천 (recommendedTrack/Content 결정에 사용)
     * @param characterSuggestions Track B 추천 (recommendedTrack/Content 결정에 사용)
     */
    fun record(
        userId: UUID,
        emotion: Emotion,
        confidence: Double,
        preferredMode: String?,
        topicSuggestions: List<EmotionRecommender.TopicSuggestion>,
        characterSuggestions: List<EmotionRecommender.CharacterSuggestion>,
    ): EmotionLog {
        val recommendedContent: String? = when {
            topicSuggestions.isNotEmpty() -> "topic:" + topicSuggestions[0].topicId
            characterSuggestions.isNotEmpty() -> "mission:" + characterSuggestions[0].character.lowercase()
            else -> null
        }

        val log = EmotionLog(
            id = null,
            userId = userId,
            appSessionId = null,
            classifiedEmotion = emotion.name,
            confidence = BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP),
            intensity = null,
            chosenDimension = preferredMode,
            recommendedTrack = if (topicSuggestions.isEmpty()) "B" else "A",
            recommendedContent = recommendedContent,
            createdAt = LocalDateTime.now(ZoneOffset.UTC),
        )
        return logs.save(log)
    }
}
