package github.lms.lemuel.xr.outbox.application

import github.lms.lemuel.xr.outbox.application.port.out.OutboxEventPort
import github.lms.lemuel.xr.outbox.domain.OutboxEvent
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * outbox/application 단위 테스트 — PublishEventUseCase INSERT + OutboxRelayJob pending→sent 배치.
 *
 * Round 2 헥사고날: 포트는 순수 도메인 [OutboxEvent] 만 오간다.
 */
class OutboxApplicationTest {

    private val repo: OutboxEventPort = mock()

    // ─────────────────────────── PublishEventUseCase ───────────────────────────

    @Test
    fun `publish pending 이벤트 INSERT`() {
        val useCase = PublishEventUseCase(repo)
        whenever(repo.save(any())).thenAnswer { it.getArgument(0) }

        val payload = mapOf<String, Any>("k" to "v", "n" to 42)
        val id = useCase.execute("session", "sess-1", "session.completed", payload)

        assertThat(id).isNotNull()
        val cap = argumentCaptor<OutboxEvent>()
        verify(repo).save(cap.capture())
        val saved = cap.firstValue
        assertThat(saved.id).isEqualTo(id)
        assertThat(saved.aggregateType).isEqualTo("session")
        assertThat(saved.aggregateId).isEqualTo("sess-1")
        assertThat(saved.eventType).isEqualTo("session.completed")
        assertThat(saved.payload).isEqualTo(payload)
        assertThat(saved.status).isEqualTo("pending")
        assertThat(saved.createdAt).isNotNull()
    }

    // ─────────────────────────────── OutboxRelayJob ───────────────────────────────

    @Test
    fun `relay 빈배치면 아무것도 저장안함`() {
        val job = OutboxRelayJob(repo)
        whenever(repo.findByStatus(eq("pending"), any())).thenReturn(listOf())

        job.relay()

        // early return — save 호출 없음.
        verify(repo).findByStatus(eq("pending"), any())
        verify(repo, never()).save(any())
    }

    @Test
    fun `relay pending 이벤트 sent 마킹`() {
        val job = OutboxRelayJob(repo)
        val e1 = newPending()
        val e2 = newPending()
        whenever(repo.findByStatus(eq("pending"), any())).thenReturn(listOf(e1, e2))
        whenever(repo.save(any())).thenAnswer { it.getArgument(0) }

        job.relay()

        val cap = argumentCaptor<OutboxEvent>()
        verify(repo, times(2)).save(cap.capture())
        for (saved in cap.allValues) {
            assertThat(saved.status).isEqualTo("sent")
            assertThat(saved.sentAt).isNotNull()
        }
    }

    @Test
    fun `relay 예외발생시 failed 마킹 attempt 증가`() {
        val job = OutboxRelayJob(repo)
        val pending = newPending()
        whenever(repo.findByStatus(eq("pending"), any())).thenReturn(listOf(pending))
        // sent 전이 저장은 실패시키고, 이어지는 failed 전이 저장만 통과.
        whenever(repo.save(argThat { status == "sent" }))
            .thenThrow(RuntimeException("boom"))

        job.relay()

        // catch 브랜치: failed + attemptCount 증가 + lastError 저장.
        val cap = argumentCaptor<OutboxEvent>()
        verify(repo, times(2)).save(cap.capture())
        val failed = cap.allValues[1]
        assertThat(failed.status).isEqualTo("failed")
        assertThat(failed.attemptCount).isEqualTo(1.toShort())
        assertThat(failed.lastError).isEqualTo("boom")
    }

    private fun newPending(): OutboxEvent =
        OutboxEvent.pending(
            UUID.randomUUID(), "session", "sess-x", "session.completed",
            mapOf(), LocalDateTime.now(),
        )
}
