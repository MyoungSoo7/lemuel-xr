package github.lms.lemuel.xr.content.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPsalmRepository extends JpaRepository<UserPsalmJpaEntity, UUID> {
}
