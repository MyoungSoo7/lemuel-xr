package github.lms.lemuel.xr.content.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PracticeReflectionJpaRepository : JpaRepository<PracticeReflectionJpaEntity, Long> {

    fun findByUserIdAndTopicIdOrderByCreatedAtDesc(
        userId: UUID,
        topicId: Short,
        pageable: Pageable,
    ): List<PracticeReflectionJpaEntity>

    fun countByUserIdAndTopicIdAndActionTakenTrue(userId: UUID, topicId: Short): Long
}
