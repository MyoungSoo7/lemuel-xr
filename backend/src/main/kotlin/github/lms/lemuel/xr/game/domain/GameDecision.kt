package github.lms.lemuel.xr.game.domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * 한 Scene 에서의 사용자 결정 기록(도메인).
 *
 * 프레임워크 무관 — 영속화는 `GameDecisionPersistenceAdapter` 가
 * `GameDecisionJpaEntity` 와 매핑한다. `id` 는 저장 후 DB 가 채워주는 식별자로,
 * 신규 결정 기록 시에는 `null`.
 */
data class GameDecision(
    val id: Long?,
    val gameSessionId: UUID,
    val sceneNumber: Short,
    val sceneName: String?,
    val decision: Map<String, Any?>?,
    val interactionMeta: Map<String, Any?>?,
    val decidedAt: LocalDateTime,
) {
    companion object {
        /** 신규 결정 기록 팩토리 — id 는 저장 시 DB 가 채운다. */
        fun record(
            gameSessionId: UUID,
            sceneNumber: Int,
            sceneName: String?,
            decision: Map<String, Any?>?,
            interactionMeta: Map<String, Any?>?,
        ): GameDecision =
            GameDecision(
                null, gameSessionId, sceneNumber.toShort(), sceneName,
                decision, interactionMeta, LocalDateTime.now(),
            )
    }
}
