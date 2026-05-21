package github.lms.lemuel.xr.content.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicContentRepository extends JpaRepository<TopicContentJpaEntity, Long> {

    @Query("SELECT t FROM TopicContentJpaEntity t WHERE t.topicId = :topicId AND t.active = true " +
           "AND (:emotion IS NULL OR t.targetEmotion = :emotion OR t.targetEmotion IS NULL) " +
           "ORDER BY t.difficulty ASC, t.id ASC")
    List<TopicContentJpaEntity> findRelevant(@Param("topicId") short topicId,
                                              @Param("emotion") String emotion,
                                              Pageable page);

    Optional<TopicContentJpaEntity> findFirstByTopicIdAndActiveTrueOrderByPublishedAtDesc(short topicId);
}
