package github.lms.lemuel.xr.game.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDecisionRepository extends JpaRepository<GameDecisionJpaEntity, Long> {
    List<GameDecisionJpaEntity> findByGameSessionIdOrderBySceneNumber(UUID gameSessionId);
}
