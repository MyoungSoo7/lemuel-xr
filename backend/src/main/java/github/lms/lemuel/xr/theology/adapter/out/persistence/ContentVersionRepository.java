package github.lms.lemuel.xr.theology.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVersionRepository extends JpaRepository<ContentVersionJpaEntity, UUID> {
    Optional<ContentVersionJpaEntity> findFirstByContentKindAndContentRefAndStatusOrderByPublishedAtDesc(
        String contentKind, String contentRef, String status);

    /** 검토 큐 — 신학·임상 자문가가 보는 pending 콘텐츠 목록. */
    List<ContentVersionJpaEntity> findByStatusOrderByCreatedAtAsc(String status);
}
