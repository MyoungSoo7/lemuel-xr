package github.lms.lemuel.xr.content.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeReflectionRepository extends JpaRepository<PracticeReflectionJpaEntity, Long> {

    List<PracticeReflectionJpaEntity> findByUserIdAndTopicIdOrderByCreatedAtDesc(
            UUID userId, Short topicId, Pageable pageable);

    long countByUserIdAndTopicIdAndActionTakenTrue(UUID userId, Short topicId);
}
