package github.lms.lemuel.xr.theology.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalReviewRepository extends JpaRepository<ClinicalReviewJpaEntity, Long> {

    List<ClinicalReviewJpaEntity> findByContentVersionId(UUID contentVersionId);

    boolean existsByContentVersionIdAndReviewerId(UUID contentVersionId, UUID reviewerId);
}
