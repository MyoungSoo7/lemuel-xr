package github.lms.lemuel.xr.safety.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyAlertRepository extends JpaRepository<SafetyAlertJpaEntity, Long> {
    List<SafetyAlertJpaEntity> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime after);
    long countBySeverityAndCreatedAtAfter(String severity, LocalDateTime after);
}
