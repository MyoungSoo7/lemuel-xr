package github.lms.lemuel.xr.outbox.application

import github.lms.lemuel.xr.outbox.application.port.out.OutboxEventPort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * outbox_events.status='pending' → 처리 후 'sent'.
 *
 * Phase 1 은 *in-process* relay — ELK ship 정도. Phase 2 에서 Kafka 또는 Webhook 통합.
 *
 * 도메인 [github.lms.lemuel.xr.outbox.domain.OutboxEvent] 는 불변이므로 상태 전이는 새 인스턴스를 만들어
 * [OutboxEventPort.save] 로 다시 영속화한다 (같은 트랜잭션 안에서 pending→sent/failed).
 */
@Component
class OutboxRelayJob(
    private val repo: OutboxEventPort,
) {

    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(name = "xr-outbox-relay", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    @Transactional
    fun relay() {
        val batch = repo.findByStatus("pending", PageRequest.of(0, 50))
        if (batch.isEmpty()) return
        for (e in batch) {
            try {
                // TODO Phase 2: 실제 발행 (Kafka / Webhook).
                // 현재는 단순 mark as sent.
                repo.save(e.markSent(LocalDateTime.now()))
            } catch (ex: Exception) {
                repo.save(e.markFailed(ex.message))
                log.warn("Outbox relay 실패 id={}: {}", e.id, ex.message)
            }
        }
        log.info("Outbox relay 완료: {} events", batch.size)
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutboxRelayJob::class.java)
    }
}
