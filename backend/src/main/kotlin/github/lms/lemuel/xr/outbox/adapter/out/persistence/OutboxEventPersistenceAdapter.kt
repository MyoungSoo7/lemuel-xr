package github.lms.lemuel.xr.outbox.adapter.out.persistence

import github.lms.lemuel.xr.outbox.application.port.out.OutboxEventPort
import github.lms.lemuel.xr.outbox.domain.OutboxEvent
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * [OutboxEventPort] 구현 — Spring Data [OutboxEventJpaRepository] 위임.
 *
 * 이 클래스가 outbox 컨텍스트에서 [OutboxEventJpaEntity] 를 import 하는 유일한 지점.
 * 도메인 [OutboxEvent] ↔ Hibernate 엔티티 매핑을 전담한다.
 */
@Component
class OutboxEventPersistenceAdapter(
    private val jpa: OutboxEventJpaRepository,
) : OutboxEventPort {

    override fun save(event: OutboxEvent): OutboxEvent =
        toDomain(jpa.save(toEntity(event)))

    override fun findByStatus(status: String, page: Pageable): List<OutboxEvent> =
        jpa.findByStatus(status, page).map(::toDomain)

    private fun toEntity(d: OutboxEvent): OutboxEventJpaEntity =
        OutboxEventJpaEntity().apply {
            id = d.id
            aggregateType = d.aggregateType
            aggregateId = d.aggregateId
            eventType = d.eventType
            payload = d.payload
            headers = d.headers
            status = d.status
            attemptCount = d.attemptCount
            lastError = d.lastError
            createdAt = d.createdAt
            sentAt = d.sentAt
        }

    private fun toDomain(e: OutboxEventJpaEntity): OutboxEvent =
        OutboxEvent(
            id = e.id!!,
            aggregateType = e.aggregateType!!,
            aggregateId = e.aggregateId!!,
            eventType = e.eventType!!,
            payload = e.payload,
            headers = e.headers,
            status = e.status,
            attemptCount = e.attemptCount,
            lastError = e.lastError,
            createdAt = e.createdAt!!,
            sentAt = e.sentAt,
        )
}
