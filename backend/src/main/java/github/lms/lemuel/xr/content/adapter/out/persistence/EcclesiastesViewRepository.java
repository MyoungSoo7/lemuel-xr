package github.lms.lemuel.xr.content.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EcclesiastesViewRepository extends JpaRepository<EcclesiastesViewJpaEntity, Long> {

    List<EcclesiastesViewJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** §4.4 신호 — 결론(전 12:13)까지 함께 본 view 누적 수. nihilism 방지 지표. */
    long countByUserIdAndConclusionViewedTrue(UUID userId);
}
