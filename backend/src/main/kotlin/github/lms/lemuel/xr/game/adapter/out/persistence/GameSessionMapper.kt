package github.lms.lemuel.xr.game.adapter.out.persistence

import github.lms.lemuel.xr.game.domain.GameSession

/**
 * [GameSession](도메인) ↔ [GameSessionJpaEntity](Hibernate) 매퍼.
 * 어댑터 경계 내부 전용 — JPA 엔티티 참조는 이 클래스와 어댑터에만 존재한다.
 */
internal object GameSessionMapper {

    fun toDomain(e: GameSessionJpaEntity): GameSession =
        GameSession.reconstitute(
            e.id,
            e.userId,
            e.appSessionId,
            e.character,
            e.chosenDimension,
            e.startedAt,
            e.completedAt,
            e.abandonedAt,
            e.triggeredByEmotionLogId,
            e.decisions,
            e.finalOutcome,
            e.closingMessage,
            e.sceneCountCompleted,
            e.durationSeconds,
            e.deviceType,
            e.capabilities,
            e.assetsManifestVersion,
        )

    fun toEntity(d: GameSession): GameSessionJpaEntity =
        GameSessionJpaEntity().apply {
            id = d.id
            userId = d.userId
            appSessionId = d.appSessionId
            character = d.character
            chosenDimension = d.chosenDimension
            startedAt = d.startedAt
            completedAt = d.completedAt
            abandonedAt = d.abandonedAt
            triggeredByEmotionLogId = d.triggeredByEmotionLogId
            decisions = d.decisions
            finalOutcome = d.finalOutcome
            closingMessage = d.closingMessage
            sceneCountCompleted = d.sceneCountCompleted
            durationSeconds = d.durationSeconds
            deviceType = d.deviceType
            capabilities = d.capabilities
            assetsManifestVersion = d.assetsManifestVersion
        }
}
