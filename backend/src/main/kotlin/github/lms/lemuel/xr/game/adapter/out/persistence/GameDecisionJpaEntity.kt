package github.lms.lemuel.xr.game.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "game_decisions")
class GameDecisionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "game_session_id", nullable = false)
    var gameSessionId: UUID? = null

    @Column(name = "scene_number", nullable = false)
    var sceneNumber: Short? = null

    @Column(name = "scene_name", length = 50)
    var sceneName: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var decision: Map<String, Any?>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interaction_meta", columnDefinition = "jsonb")
    var interactionMeta: Map<String, Any?>? = null

    @Column(name = "decided_at", nullable = false)
    var decidedAt: LocalDateTime? = null
}
