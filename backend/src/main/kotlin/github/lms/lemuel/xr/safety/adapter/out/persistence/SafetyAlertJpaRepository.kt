package github.lms.lemuel.xr.safety.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface SafetyAlertJpaRepository : JpaRepository<SafetyAlertJpaEntity, Long> {

    fun findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
        userId: UUID,
        after: LocalDateTime,
    ): List<SafetyAlertJpaEntity>

    fun countBySeverityAndCreatedAtAfter(severity: String, after: LocalDateTime): Long
}
