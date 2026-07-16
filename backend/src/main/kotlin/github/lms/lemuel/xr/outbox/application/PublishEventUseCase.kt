package github.lms.lemuel.xr.outbox.application

import github.lms.lemuel.xr.outbox.application.port.out.OutboxEventPort
import github.lms.lemuel.xr.outbox.domain.OutboxEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/** 트랜잭션 일관성 보장된 이벤트 발행 — 호출자 transaction 안에 INSERT. */
@Service
class PublishEventUseCase(
    private val repo: OutboxEventPort,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun execute(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Map<String, Any>?,
    ): UUID {
        val id = UUID.randomUUID()
        val event = OutboxEvent.pending(
            id, aggregateType, aggregateId, eventType, payload, LocalDateTime.now(),
        )
        repo.save(event)
        return id
    }
}
