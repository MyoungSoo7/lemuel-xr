package github.lms.lemuel.xr.game.adapter.out.persistence

import github.lms.lemuel.xr.game.application.port.out.SceneViewPort
import github.lms.lemuel.xr.game.domain.SceneView
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [SceneViewPort] 어댑터 — [SceneViewJpaEntity] 를 순수 도메인
 * [SceneView] 로 매핑해 반환한다.
 */
@Component
class SceneViewPersistenceAdapter(
    private val repository: SceneViewJpaRepository,
) : SceneViewPort {

    override fun findByGameSessionIdOrderByEnteredAt(gameSessionId: UUID): List<SceneView> =
        repository.findByGameSessionIdOrderByEnteredAt(gameSessionId).map(::toDomain)

    private fun toDomain(e: SceneViewJpaEntity): SceneView =
        SceneView(
            e.id,
            e.gameSessionId!!,
            e.sceneNumber!!,
            e.enteredAt!!,
            e.exitedAt,
            e.durationSeconds,
            e.exitReason,
            e.skippedSilence,
        )
}
