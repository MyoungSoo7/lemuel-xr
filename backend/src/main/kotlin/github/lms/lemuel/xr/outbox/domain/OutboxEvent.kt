package github.lms.lemuel.xr.outbox.domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * outbox_events 애그리거트 — 순수 도메인 모델 (Hibernate 비의존).
 *
 * 불변 data class. PENDING→PUBLISHED(sent) 상태머신은 [markSent] / [markFailed] 로 새 인스턴스를
 * 반환하며 보존된다. [id] 가 event_id 시맨틱(settlement Triple Idempotency 의 L0)을 그대로 유지한다.
 */
data class OutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: Map<String, Any>?,
    val headers: Map<String, Any>?,
    val status: String,
    val attemptCount: Short,
    val lastError: String?,
    val createdAt: LocalDateTime,
    val sentAt: LocalDateTime?,
) {

    /** pending → sent 전이. sentAt 을 기록한 새 인스턴스 반환. */
    fun markSent(sentAt: LocalDateTime): OutboxEvent =
        copy(status = STATUS_SENT, sentAt = sentAt)

    /** 발행 실패 전이. attemptCount 증가 + lastError 기록한 새 인스턴스 반환. */
    fun markFailed(error: String?): OutboxEvent =
        copy(status = STATUS_FAILED, attemptCount = (attemptCount + 1).toShort(), lastError = error)

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"

        /** 호출자 트랜잭션 안에서 INSERT 될 새 pending 이벤트를 생성한다. */
        fun pending(
            id: UUID,
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: Map<String, Any>?,
            createdAt: LocalDateTime,
        ): OutboxEvent =
            OutboxEvent(
                id = id,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload,
                headers = null,
                status = STATUS_PENDING,
                attemptCount = 0,
                lastError = null,
                createdAt = createdAt,
                sentAt = null,
            )
    }
}
