package github.lms.lemuel.xr.game.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "scene_views")
class SceneViewJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "game_session_id", nullable = false)
    var gameSessionId: UUID? = null

    @Column(name = "scene_number", nullable = false)
    var sceneNumber: Short? = null

    @Column(name = "entered_at", nullable = false)
    var enteredAt: LocalDateTime? = null

    @Column(name = "exited_at")
    var exitedAt: LocalDateTime? = null

    @Column(name = "duration_seconds", insertable = false, updatable = false)
    var durationSeconds: Int? = null

    @Column(name = "exit_reason", length = 30)
    var exitReason: String? = null

    @Column(name = "skipped_silence")
    var skippedSilence: Boolean? = false
}
