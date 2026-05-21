package github.lms.lemuel.xr.theology.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVersionRepository extends JpaRepository<ContentVersionJpaEntity, UUID> {
    Optional<ContentVersionJpaEntity> findFirstByContentKindAndContentRefAndStatusOrderByPublishedAtDesc(
        String contentKind, String contentRef, String status);
}
