package github.lms.lemuel.xr.game.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "game_sessions")
class GameSessionJpaEntity {
    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "app_session_id")
    var appSessionId: UUID? = null

    @Column(nullable = false, length = 20)
    var character: String? = null

    @Column(name = "chosen_dimension", length = 20)
    var chosenDimension: String? = null

    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime? = null

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null

    @Column(name = "abandoned_at")
    var abandonedAt: LocalDateTime? = null

    @Column(name = "triggered_by_emotion_log_id")
    var triggeredByEmotionLogId: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decisions", columnDefinition = "jsonb")
    var decisions: MutableMap<String, Any?> = HashMap()

    @Column(name = "final_outcome", length = 50)
    var finalOutcome: String? = null

    @Column(name = "closing_message", columnDefinition = "text")
    var closingMessage: String? = null

    @Column(name = "scene_count_completed")
    var sceneCountCompleted: Short? = 0

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null

    @Column(name = "device_type", length = 30)
    var deviceType: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    var capabilities: Map<String, Any?>? = null

    @Column(name = "assets_manifest_version", length = 20)
    var assetsManifestVersion: String? = null
}
