package github.lms.lemuel.xr.recovery.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryMetricRepository extends JpaRepository<RecoveryMetricJpaEntity, Long> {

    @Query("SELECT r FROM RecoveryMetricJpaEntity r WHERE r.userId = :userId " +
           "AND r.metricDate >= :since ORDER BY r.metricDate DESC")
    List<RecoveryMetricJpaEntity> findRecent(@Param("userId") UUID userId, @Param("since") LocalDate since);
}
