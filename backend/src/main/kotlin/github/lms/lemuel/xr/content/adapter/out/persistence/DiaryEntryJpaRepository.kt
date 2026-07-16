package github.lms.lemuel.xr.content.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DiaryEntryJpaRepository : JpaRepository<DiaryEntryJpaEntity, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<DiaryEntryJpaEntity>
}
