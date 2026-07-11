package github.lms.lemuel.xr.game.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneViewRepository extends JpaRepository<SceneViewJpaEntity, Long> {
    List<SceneViewJpaEntity> findByGameSessionIdOrderByEnteredAt(UUID gameSessionId);
}
