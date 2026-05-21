package github.lms.lemuel.xr.outbox.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventJpaEntity, ProcessedEventJpaEntity.PK> {
}
