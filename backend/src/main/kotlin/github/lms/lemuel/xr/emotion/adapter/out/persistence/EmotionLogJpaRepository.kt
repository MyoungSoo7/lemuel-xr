package github.lms.lemuel.xr.emotion.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface EmotionLogJpaRepository : JpaRepository<EmotionLogJpaEntity, Long> {

    fun findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
        userId: UUID,
        after: LocalDateTime,
    ): List<EmotionLogJpaEntity>

    @Query(
        "SELECT e FROM EmotionLogJpaEntity e WHERE e.userId = :userId " +
            "AND (:since IS NULL OR e.createdAt >= :since) " +
            "ORDER BY e.createdAt DESC",
    )
    fun findRecent(
        @Param("userId") userId: UUID,
        @Param("since") since: LocalDateTime?,
        page: Pageable,
    ): List<EmotionLogJpaEntity>
}
