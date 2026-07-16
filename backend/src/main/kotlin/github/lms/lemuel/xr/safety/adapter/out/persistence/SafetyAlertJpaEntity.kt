package github.lms.lemuel.xr.safety.adapter.out.persistence

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
@Table(name = "safety_alerts")
class SafetyAlertJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id")
    var userId: UUID? = null

    @Column(name = "app_session_id")
    var appSessionId: UUID? = null

    @Column(name = "emotion_log_id")
    var emotionLogId: Long? = null

    @Column(name = "matched_pattern", nullable = false, length = 50)
    var matchedPattern: String? = null

    @Column(nullable = false, length = 10)
    var severity: String? = null

    @Column(name = "trigger_source", nullable = false, length = 20)
    var triggerSource: String? = null

    @Column(name = "raw_excerpt_hash", length = 64)
    var rawExcerptHash: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shown_resources", columnDefinition = "jsonb")
    var shownResources: MutableList<Map<String, Any>>? = null

    @Column(name = "user_acknowledged")
    var userAcknowledged: Boolean? = false

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
