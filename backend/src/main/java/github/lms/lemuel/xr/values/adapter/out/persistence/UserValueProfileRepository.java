package github.lms.lemuel.xr.values.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserValueProfileRepository extends JpaRepository<UserValueProfileJpaEntity, UUID> {
    Optional<UserValueProfileJpaEntity> findByUserId(UUID userId);
}
