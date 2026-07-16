package github.lms.lemuel.xr.emotion.adapter.out.persistence

import github.lms.lemuel.xr.emotion.application.port.out.EmotionLogPort
import github.lms.lemuel.xr.emotion.domain.EmotionLog
import org.springframework.stereotype.Component

/**
 * [EmotionLogPort] → Spring Data [EmotionLogJpaRepository] 위임 어댑터.
 *
 * emotion 컨텍스트에서 [EmotionLogJpaEntity] 를 import 하는 **유일한** 지점.
 * 도메인 [EmotionLog] ↔ JPA 엔티티 매핑을 여기서만 수행한다.
 */
@Component
class EmotionLogPersistenceAdapter(
    private val jpa: EmotionLogJpaRepository,
) : EmotionLogPort {

    override fun save(log: EmotionLog): EmotionLog =
        toDomain(jpa.save(toEntity(log)))

    private fun toEntity(log: EmotionLog): EmotionLogJpaEntity =
        EmotionLogJpaEntity().apply {
            id = log.id
            userId = log.userId
            appSessionId = log.appSessionId
            classifiedEmotion = log.classifiedEmotion
            confidence = log.confidence
            intensity = log.intensity
            chosenDimension = log.chosenDimension
            recommendedTrack = log.recommendedTrack
            recommendedContent = log.recommendedContent
            createdAt = log.createdAt
        }

    private fun toDomain(entity: EmotionLogJpaEntity): EmotionLog =
        EmotionLog(
            id = entity.id,
            userId = entity.userId,
            appSessionId = entity.appSessionId,
            classifiedEmotion = entity.classifiedEmotion,
            confidence = entity.confidence,
            intensity = entity.intensity,
            chosenDimension = entity.chosenDimension,
            recommendedTrack = entity.recommendedTrack,
            recommendedContent = entity.recommendedContent,
            createdAt = entity.createdAt,
        )
}
