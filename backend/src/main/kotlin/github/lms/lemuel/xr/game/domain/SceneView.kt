package github.lms.lemuel.xr.game.domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * Scene 진입 이력(도메인).
 *
 * 프레임워크 무관 — 영속화는 `SceneViewPersistenceAdapter` 가
 * `SceneViewJpaEntity` 와 매핑한다.
 */
data class SceneView(
    val id: Long?,
    val gameSessionId: UUID,
    val sceneNumber: Short,
    val enteredAt: LocalDateTime,
    val exitedAt: LocalDateTime?,
    val durationSeconds: Int?,
    val exitReason: String?,
    val skippedSilence: Boolean?,
)
