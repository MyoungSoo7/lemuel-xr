package github.lms.lemuel.xr.theology.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheologyReviewRepository extends JpaRepository<TheologyReviewJpaEntity, Long> {
    List<TheologyReviewJpaEntity> findByContentVersionIdOrderByReviewedAtDesc(UUID contentVersionId);

    List<TheologyReviewJpaEntity> findByContentVersionId(UUID contentVersionId);
}
