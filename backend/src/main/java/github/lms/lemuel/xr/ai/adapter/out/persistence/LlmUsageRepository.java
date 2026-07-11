package github.lms.lemuel.xr.ai.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmUsageRepository extends JpaRepository<LlmUsageJpaEntity, Long> {
    List<LlmUsageJpaEntity> findByOccurredAtAfter(LocalDateTime after);
}
