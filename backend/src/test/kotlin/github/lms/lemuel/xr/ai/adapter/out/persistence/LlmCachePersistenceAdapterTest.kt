package github.lms.lemuel.xr.ai.adapter.out.persistence

import github.lms.lemuel.xr.ai.domain.LlmCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

/**
 * LlmCachePersistenceAdapter 단위 테스트 — 엔티티↔도메인 매핑 왕복(toEntity/toDomain) 커버.
 *
 * [LlmCacheJpaRepository] 는 mockito-kotlin 으로 목킹. 모든 필드를 채워 매핑 라인 전체를 실행한다.
 */
class LlmCachePersistenceAdapterTest {

    private val jpa: LlmCacheJpaRepository = mock()
    private val adapter = LlmCachePersistenceAdapter(jpa)

    private val lastHit: LocalDateTime = LocalDateTime.of(2026, 7, 1, 10, 0)
    private val created: LocalDateTime = LocalDateTime.of(2026, 6, 30, 9, 0)
    private val expires: LocalDateTime = LocalDateTime.of(2026, 7, 30, 9, 0)

    private fun fullEntity(): LlmCacheJpaEntity =
        LlmCacheJpaEntity().apply {
            cacheKey = "key-abc"
            response = "cached body"
            provider = "openai"
            model = "gpt-4o"
            purpose = "meditation"
            promptTokens = 120
            completionTokens = 340
            hitCount = 7
            lastHitAt = lastHit
            createdAt = created
            expiresAt = expires
        }

    private fun fullDomain(): LlmCache =
        LlmCache(
            "key-abc", "cached body", "openai", "gpt-4o", "meditation",
            120, 340, 7, lastHit, created, expires,
        )

    @Test
    fun `save 는 toEntity 로 변환하고 저장결과를 toDomain 으로 반환한다`() {
        whenever(jpa.save(any())).thenReturn(fullEntity())

        val result = adapter.save(fullDomain())

        val captor = argumentCaptor<LlmCacheJpaEntity>()
        verify(jpa).save(captor.capture())
        val persisted = captor.firstValue
        assertThat(persisted.cacheKey).isEqualTo("key-abc")
        assertThat(persisted.response).isEqualTo("cached body")
        assertThat(persisted.provider).isEqualTo("openai")
        assertThat(persisted.model).isEqualTo("gpt-4o")
        assertThat(persisted.purpose).isEqualTo("meditation")
        assertThat(persisted.promptTokens).isEqualTo(120)
        assertThat(persisted.completionTokens).isEqualTo(340)
        assertThat(persisted.hitCount).isEqualTo(7)
        assertThat(persisted.lastHitAt).isEqualTo(lastHit)
        assertThat(persisted.createdAt).isEqualTo(created)
        assertThat(persisted.expiresAt).isEqualTo(expires)

        assertThat(result).isEqualTo(fullDomain())
    }

    @Test
    fun `findByCacheKey 는 엔티티를 도메인으로 매핑해 반환한다`() {
        whenever(jpa.findById("key-abc")).thenReturn(Optional.of(fullEntity()))

        val result = adapter.findByCacheKey("key-abc")

        assertThat(result).contains(fullDomain())
    }

    @Test
    fun `findByCacheKey 는 미존재시 empty 를 반환한다`() {
        whenever(jpa.findById("missing")).thenReturn(Optional.empty())

        assertThat(adapter.findByCacheKey("missing")).isEmpty()
    }
}
