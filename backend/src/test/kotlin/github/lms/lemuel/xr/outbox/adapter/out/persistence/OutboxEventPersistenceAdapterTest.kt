package github.lms.lemuel.xr.outbox.adapter.out.persistence

import github.lms.lemuel.xr.outbox.domain.OutboxEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.UUID

/**
 * OutboxEventPersistenceAdapter 단위 테스트 — 도메인↔엔티티 왕복 매핑 커버.
 */
class OutboxEventPersistenceAdapterTest {

    private val jpa: OutboxEventJpaRepository = mock()
    private val adapter = OutboxEventPersistenceAdapter(jpa)

    private val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val created = LocalDateTime.of(2026, 7, 3, 8, 0)
    private val sent = LocalDateTime.of(2026, 7, 3, 8, 5)
    private val payload = mapOf<String, Any>("k" to "v")
    private val headers = mapOf<String, Any>("h" to "1")

    private fun fullEntity(): OutboxEventJpaEntity {
        val e = OutboxEventJpaEntity()
        e.id = id
        e.aggregateType = "game_session"
        e.aggregateId = "agg-1"
        e.eventType = "SessionCompleted"
        e.payload = payload
        e.headers = headers
        e.status = "sent"
        e.attemptCount = 3
        e.lastError = "boom"
        e.createdAt = created
        e.sentAt = sent
        return e
    }

    private fun fullDomain(): OutboxEvent =
        OutboxEvent(
            id, "game_session", "agg-1", "SessionCompleted", payload,
            headers, "sent", 3, "boom", created, sent,
        )

    @Test
    fun `save 는 toEntity 변환후 저장결과를 도메인으로 반환한다`() {
        whenever(jpa.save(any())).thenReturn(fullEntity())

        val result = adapter.save(fullDomain())

        val captor = argumentCaptor<OutboxEventJpaEntity>()
        verify(jpa).save(captor.capture())
        val persisted = captor.firstValue
        assertThat(persisted.id).isEqualTo(id)
        assertThat(persisted.aggregateType).isEqualTo("game_session")
        assertThat(persisted.aggregateId).isEqualTo("agg-1")
        assertThat(persisted.eventType).isEqualTo("SessionCompleted")
        assertThat(persisted.payload).isEqualTo(payload)
        assertThat(persisted.headers).isEqualTo(headers)
        assertThat(persisted.status).isEqualTo("sent")
        assertThat(persisted.attemptCount).isEqualTo(3.toShort())
        assertThat(persisted.lastError).isEqualTo("boom")
        assertThat(persisted.createdAt).isEqualTo(created)
        assertThat(persisted.sentAt).isEqualTo(sent)

        assertThat(result).isEqualTo(fullDomain())
    }

    @Test
    fun `findByStatus 는 리스트를 도메인으로 매핑한다`() {
        val page: Pageable = PageRequest.of(0, 10)
        whenever(jpa.findByStatus(eq("pending"), any())).thenReturn(listOf(fullEntity()))

        val result = adapter.findByStatus("pending", page)

        assertThat(result).containsExactly(fullDomain())
    }

    @Test
    fun `toDomain 은 attemptCount 기본값이면 0이다`() {
        // Kotlin 엔티티의 attemptCount 는 non-null Short(기본 0) — null 을 담을 수 없어
        // 미설정(기본 0) 경로로 "0" 매핑을 검증한다.
        val e = fullEntity()
        e.attemptCount = 0
        whenever(jpa.findByStatus(eq("pending"), any())).thenReturn(listOf(e))

        val result = adapter.findByStatus("pending", PageRequest.of(0, 1))[0]

        assertThat(result.attemptCount).isEqualTo(0.toShort())
    }
}
