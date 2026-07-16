package github.lms.lemuel.xr.outbox.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query(
        "SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status " +
            "ORDER BY e.createdAt ASC",
    )
    fun findByStatus(@Param("status") status: String, page: Pageable): List<OutboxEventJpaEntity>
}
