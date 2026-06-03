package github.lms.lemuel.xr.outbox.application;

import github.lms.lemuel.xr.outbox.adapter.out.persistence.OutboxEventJpaEntity;
import github.lms.lemuel.xr.outbox.adapter.out.persistence.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox_events.status='pending' → 처리 후 'sent'.
 *
 * <p>Phase 1 은 *in-process* relay — ELK ship 정도. Phase 2 에서 Kafka 또는 Webhook 통합.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private final OutboxEventRepository repo;

    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(name = "xr-outbox-relay", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    @Transactional
    public void relay() {
        List<OutboxEventJpaEntity> batch = repo.findByStatus("pending", PageRequest.of(0, 50));
        if (batch.isEmpty()) return;
        for (OutboxEventJpaEntity e : batch) {
            try {
                // TODO Phase 2: 실제 발행 (Kafka / Webhook).
                // 현재는 단순 mark as sent.
                e.setStatus("sent");
                e.setSentAt(LocalDateTime.now());
            } catch (Exception ex) {
                e.setStatus("failed");
                e.setAttemptCount((short) (e.getAttemptCount() + 1));
                e.setLastError(ex.getMessage());
                log.warn("Outbox relay 실패 id={}: {}", e.getId(), ex.getMessage());
            }
        }
        log.info("Outbox relay 완료: {} events", batch.size());
    }
}
