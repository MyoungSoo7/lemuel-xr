package github.lms.lemuel.xr.outbox.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

/** outbox_events (V11) — settlement Triple Idempotency 의 L0. */
@Entity
@Table(name = "outbox_events")
class OutboxEventJpaEntity {

    @Id
    var id: UUID? = null

    @Column(name = "aggregate_type", nullable = false, length = 50)
    var aggregateType: String? = null

    @Column(name = "aggregate_id", nullable = false, length = 80)
    var aggregateId: String? = null

    @Column(name = "event_type", nullable = false, length = 80)
    var eventType: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    var payload: Map<String, Any>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    var headers: Map<String, Any>? = null

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "pending"

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Short = 0

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
}
