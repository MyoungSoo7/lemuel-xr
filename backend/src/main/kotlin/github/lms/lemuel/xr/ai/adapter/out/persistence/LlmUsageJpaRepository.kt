package github.lms.lemuel.xr.ai.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface LlmUsageJpaRepository : JpaRepository<LlmUsageJpaEntity, Long> {
    fun findByOccurredAtAfter(after: LocalDateTime): List<LlmUsageJpaEntity>
}
