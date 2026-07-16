package github.lms.lemuel.xr.ai.adapter.out.persistence

import github.lms.lemuel.xr.ai.application.port.out.LlmUsagePort
import github.lms.lemuel.xr.ai.domain.LlmUsage
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [LlmUsagePort] → Spring Data [LlmUsageJpaRepository] 위임 어댑터.
 *
 * 엔티티 ↔ 도메인([LlmUsage]) 매핑의 **유일한** 장소. JPA 엔티티는 이 클래스 밖으로 새지 않는다.
 */
@Component
class LlmUsagePersistenceAdapter(
    private val jpa: LlmUsageJpaRepository,
) : LlmUsagePort {

    override fun save(usage: LlmUsage): LlmUsage =
        toDomain(jpa.save(toEntity(usage)))

    override fun findByOccurredAtAfter(after: LocalDateTime): List<LlmUsage> =
        jpa.findByOccurredAtAfter(after).map(::toDomain)

    private fun toDomain(e: LlmUsageJpaEntity): LlmUsage =
        LlmUsage(
            id = e.id,
            occurredAt = e.occurredAt!!,
            userId = e.userId,
            purpose = e.purpose!!,
            provider = e.provider!!,
            model = e.model!!,
            promptTokens = e.promptTokens,
            completionTokens = e.completionTokens,
            latencyMs = e.latencyMs,
            cacheHit = e.cacheHit,
            costUsd = e.costUsd,
            requestId = e.requestId,
            success = e.success,
            errorCode = e.errorCode,
        )

    private fun toEntity(u: LlmUsage): LlmUsageJpaEntity =
        LlmUsageJpaEntity().apply {
            id = u.id
            occurredAt = u.occurredAt
            userId = u.userId
            purpose = u.purpose
            provider = u.provider
            model = u.model
            promptTokens = u.promptTokens
            completionTokens = u.completionTokens
            latencyMs = u.latencyMs
            cacheHit = u.cacheHit
            costUsd = u.costUsd
            requestId = u.requestId
            success = u.success
            errorCode = u.errorCode
        }
}
