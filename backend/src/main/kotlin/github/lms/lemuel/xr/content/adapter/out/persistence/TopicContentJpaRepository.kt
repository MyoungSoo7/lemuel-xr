package github.lms.lemuel.xr.content.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface TopicContentJpaRepository : JpaRepository<TopicContentJpaEntity, Long> {

    @Query(
        "SELECT t FROM TopicContentJpaEntity t WHERE t.topicId = :topicId AND t.active = true " +
            "AND (:emotion IS NULL OR t.targetEmotion = :emotion OR t.targetEmotion IS NULL) " +
            "ORDER BY t.difficulty ASC, t.id ASC",
    )
    fun findRelevant(
        @Param("topicId") topicId: Short,
        @Param("emotion") emotion: String?,
        page: Pageable,
    ): List<TopicContentJpaEntity>

    fun findFirstByTopicIdAndActiveTrueOrderByPublishedAtDesc(topicId: Short): Optional<TopicContentJpaEntity>
}
