package github.lms.lemuel.xr.content.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntryJpaEntity, UUID> {
    List<DiaryEntryJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
