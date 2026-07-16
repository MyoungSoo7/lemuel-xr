package github.lms.lemuel.xr.values.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.UUID

interface UserValuePracticeJpaRepository : JpaRepository<UserValuePracticeJpaEntity, Long> {

    @Query(
        "SELECT p FROM UserValuePracticeJpaEntity p WHERE p.userId = :userId " +
            "AND p.practicedAt >= :since ORDER BY p.practicedAt DESC",
    )
    fun findRecent(
        @Param("userId") userId: UUID,
        @Param("since") since: OffsetDateTime,
    ): List<UserValuePracticeJpaEntity>

    @Query(
        "SELECT p.valueId, COUNT(p) FROM UserValuePracticeJpaEntity p " +
            "WHERE p.userId = :userId AND p.practicedAt >= :since " +
            "GROUP BY p.valueId",
    )
    fun countByValue(
        @Param("userId") userId: UUID,
        @Param("since") since: OffsetDateTime,
    ): List<Array<Any>>
}
