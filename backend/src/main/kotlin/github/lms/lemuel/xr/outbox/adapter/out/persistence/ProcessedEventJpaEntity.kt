package github.lms.lemuel.xr.outbox.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime
import java.util.UUID

/** processed_events (V11) — Triple Idempotency L1 (event consumer 측 중복 차단). */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventJpaEntity.PK::class)
class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id")
    var eventId: UUID? = null

    @Id
    @Column(name = "consumer_name", length = 80)
    var consumerName: String? = null

    @Column(name = "processed_at", nullable = false)
    var processedAt: LocalDateTime? = null

    data class PK(
        var eventId: UUID? = null,
        var consumerName: String? = null,
    ) : Serializable
}
