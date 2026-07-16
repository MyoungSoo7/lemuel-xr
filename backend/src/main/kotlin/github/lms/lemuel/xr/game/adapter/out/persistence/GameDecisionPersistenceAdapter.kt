package github.lms.lemuel.xr.game.adapter.out.persistence

import github.lms.lemuel.xr.game.application.port.out.GameDecisionPort
import github.lms.lemuel.xr.game.domain.GameDecision
import org.springframework.stereotype.Component

/**
 * [GameDecisionPort] 어댑터 — 순수 도메인 [GameDecision] 과
 * [GameDecisionJpaEntity] 사이를 매핑한다.
 */
@Component
class GameDecisionPersistenceAdapter(
    private val repository: GameDecisionJpaRepository,
) : GameDecisionPort {

    override fun save(decision: GameDecision): GameDecision =
        toDomain(repository.save(toEntity(decision)))

    private fun toEntity(d: GameDecision): GameDecisionJpaEntity =
        GameDecisionJpaEntity().apply {
            id = d.id
            gameSessionId = d.gameSessionId
            sceneNumber = d.sceneNumber
            sceneName = d.sceneName
            decision = d.decision
            interactionMeta = d.interactionMeta
            decidedAt = d.decidedAt
        }

    private fun toDomain(e: GameDecisionJpaEntity): GameDecision =
        GameDecision(
            e.id,
            e.gameSessionId!!,
            e.sceneNumber!!,
            e.sceneName,
            e.decision,
            e.interactionMeta,
            e.decidedAt!!,
        )
}
