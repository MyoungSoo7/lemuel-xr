package github.lms.lemuel.xr.recovery.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface RecoveryMetricJpaRepository : JpaRepository<RecoveryMetricJpaEntity, Long> {

    @Query(
        "SELECT r FROM RecoveryMetricJpaEntity r WHERE r.userId = :userId " +
            "AND r.metricDate >= :since ORDER BY r.metricDate DESC",
    )
    fun findRecent(
        @Param("userId") userId: UUID,
        @Param("since") since: LocalDate,
    ): List<RecoveryMetricJpaEntity>
}
