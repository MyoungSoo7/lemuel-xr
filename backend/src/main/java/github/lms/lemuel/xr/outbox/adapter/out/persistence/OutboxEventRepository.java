package github.lms.lemuel.xr.outbox.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status " +
           "ORDER BY e.createdAt ASC")
    List<OutboxEventJpaEntity> findByStatus(@Param("status") String status, Pageable page);
}
