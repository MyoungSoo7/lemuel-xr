package github.lms.lemuel.xr.safety.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrisisResourceRepository extends JpaRepository<CrisisResourceJpaEntity, Long> {
    List<CrisisResourceJpaEntity> findByRegionAndLocaleAndActiveOrderByPriority(
        String region, String locale, Boolean active);

    List<CrisisResourceJpaEntity> findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc(
        String region, String locale);
}
