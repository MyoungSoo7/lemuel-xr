package github.lms.lemuel.xr.ai.adapter.out.persistence

import github.lms.lemuel.xr.ai.application.port.out.LlmCachePort
import github.lms.lemuel.xr.ai.domain.LlmCache
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * [LlmCachePort] → Spring Data [LlmCacheJpaRepository] 위임 어댑터.
 *
 * 엔티티 ↔ 도메인([LlmCache]) 매핑의 **유일한** 장소. JPA 엔티티는 이 클래스 밖으로 새지 않는다.
 */
@Component
class LlmCachePersistenceAdapter(
    private val jpa: LlmCacheJpaRepository,
) : LlmCachePort {

    override fun findByCacheKey(cacheKey: String): Optional<LlmCache> =
        jpa.findById(cacheKey).map(::toDomain)

    override fun save(cache: LlmCache): LlmCache =
        toDomain(jpa.save(toEntity(cache)))

    private fun toDomain(e: LlmCacheJpaEntity): LlmCache =
        LlmCache(
            cacheKey = e.cacheKey!!,
            response = e.response,
            provider = e.provider,
            model = e.model,
            purpose = e.purpose,
            promptTokens = e.promptTokens,
            completionTokens = e.completionTokens,
            hitCount = e.hitCount,
            lastHitAt = e.lastHitAt,
            createdAt = e.createdAt,
            expiresAt = e.expiresAt,
        )

    private fun toEntity(c: LlmCache): LlmCacheJpaEntity =
        LlmCacheJpaEntity().apply {
            cacheKey = c.cacheKey
            response = c.response
            provider = c.provider
            model = c.model
            purpose = c.purpose
            promptTokens = c.promptTokens
            completionTokens = c.completionTokens
            hitCount = c.hitCount
            lastHitAt = c.lastHitAt
            createdAt = c.createdAt
            expiresAt = c.expiresAt
        }
}
