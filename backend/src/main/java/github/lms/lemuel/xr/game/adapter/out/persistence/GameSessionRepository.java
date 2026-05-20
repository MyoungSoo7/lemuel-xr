package github.lms.lemuel.xr.game.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSessionJpaEntity, UUID> {
}
