package github.lms.lemuel.xr.ai.adapter.out.persistence

import github.lms.lemuel.xr.ai.domain.LlmUsage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * LlmUsagePersistenceAdapter 단위 테스트 — save/finder 의 엔티티↔도메인 왕복 매핑 커버.
 */
class LlmUsagePersistenceAdapterTest {

    private val jpa: LlmUsageJpaRepository = mock()
    private val adapter = LlmUsagePersistenceAdapter(jpa)

    private val user: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val occurred: LocalDateTime = LocalDateTime.of(2026, 7, 2, 12, 0)

    private fun fullEntity(): LlmUsageJpaEntity =
        LlmUsageJpaEntity().apply {
            id = 42L
            occurredAt = occurred
            userId = user
            purpose = "scene"
            provider = "anthropic"
            model = "claude-3"
            promptTokens = 200
            completionTokens = 500
            latencyMs = 850
            cacheHit = true
            costUsd = BigDecimal("0.01234")
            requestId = "req-99"
            success = true
            errorCode = "NONE"
        }

    private fun fullDomain(): LlmUsage =
        LlmUsage(
            42L, occurred, user, "scene", "anthropic", "claude-3",
            200, 500, 850, true, BigDecimal("0.01234"), "req-99", true, "NONE",
        )

    @Test
    fun `save 는 toEntity 변환후 저장결과를 도메인으로 반환한다`() {
        whenever(jpa.save(any())).thenReturn(fullEntity())

        val result = adapter.save(fullDomain())

        val captor = argumentCaptor<LlmUsageJpaEntity>()
        verify(jpa).save(captor.capture())
        val persisted = captor.firstValue
        assertThat(persisted.id).isEqualTo(42L)
        assertThat(persisted.occurredAt).isEqualTo(occurred)
        assertThat(persisted.userId).isEqualTo(user)
        assertThat(persisted.purpose).isEqualTo("scene")
        assertThat(persisted.provider).isEqualTo("anthropic")
        assertThat(persisted.model).isEqualTo("claude-3")
        assertThat(persisted.promptTokens).isEqualTo(200)
        assertThat(persisted.completionTokens).isEqualTo(500)
        assertThat(persisted.latencyMs).isEqualTo(850)
        assertThat(persisted.cacheHit).isTrue()
        assertThat(persisted.costUsd).isEqualByComparingTo("0.01234")
        assertThat(persisted.requestId).isEqualTo("req-99")
        assertThat(persisted.success).isTrue()
        assertThat(persisted.errorCode).isEqualTo("NONE")

        assertThat(result).isEqualTo(fullDomain())
    }

    @Test
    fun `findByOccurredAtAfter 는 리스트를 도메인으로 매핑한다`() {
        whenever(jpa.findByOccurredAtAfter(occurred)).thenReturn(listOf(fullEntity()))

        val result = adapter.findByOccurredAtAfter(occurred)

        assertThat(result).containsExactly(fullDomain())
    }
}
