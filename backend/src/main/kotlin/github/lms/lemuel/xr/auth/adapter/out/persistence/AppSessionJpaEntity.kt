package github.lms.lemuel.xr.auth.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

/** app_sessions — 앱 진입~종료 단위 (V3). 게임 세션과 별개. */
@Entity
@Table(name = "app_sessions")
class AppSessionJpaEntity {

    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "device_id")
    var deviceId: UUID? = null

    @Column(name = "started_at", nullable = false)
    var startedAt: OffsetDateTime? = null

    @Column(name = "ended_at")
    var endedAt: OffsetDateTime? = null

    @Column(name = "duration_seconds", insertable = false, updatable = false)
    var durationSeconds: Int? = null  // GENERATED ALWAYS — read-only

    @Column(name = "entry_emotion", length = 30)
    var entryEmotion: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_missions", columnDefinition = "jsonb", nullable = false)
    var completedMissions: MutableList<String> = ArrayList()
}
