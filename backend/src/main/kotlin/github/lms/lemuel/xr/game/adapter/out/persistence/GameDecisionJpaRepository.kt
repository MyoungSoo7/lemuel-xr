package github.lms.lemuel.xr.game.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GameDecisionJpaRepository : JpaRepository<GameDecisionJpaEntity, Long> {
    fun findByGameSessionIdOrderBySceneNumber(gameSessionId: UUID): List<GameDecisionJpaEntity>
}
