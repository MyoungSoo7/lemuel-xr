package github.lms.lemuel.xr.theology.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentQuarantineRepository extends JpaRepository<ContentQuarantineJpaEntity, Long> {

    List<ContentQuarantineJpaEntity> findByContentVersionIdOrderByQuarantinedAtDesc(UUID contentVersionId);

    boolean existsByContentVersionIdAndVetoBy(UUID contentVersionId, String vetoBy);
}
