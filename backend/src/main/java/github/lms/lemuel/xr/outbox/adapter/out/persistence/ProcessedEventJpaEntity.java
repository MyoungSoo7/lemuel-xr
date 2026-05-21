package github.lms.lemuel.xr.outbox.adapter.out.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** processed_events (V11) — Triple Idempotency L1 (event consumer 측 중복 차단). */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventJpaEntity.PK.class)
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "consumer_name", length = 80)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID eventId;
        private String consumerName;
    }
}
