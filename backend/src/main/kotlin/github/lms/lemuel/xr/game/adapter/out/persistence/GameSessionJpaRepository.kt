package github.lms.lemuel.xr.game.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GameSessionJpaRepository : JpaRepository<GameSessionJpaEntity, UUID>
