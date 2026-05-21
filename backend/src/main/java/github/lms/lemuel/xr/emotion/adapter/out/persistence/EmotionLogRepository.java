package github.lms.lemuel.xr.emotion.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmotionLogRepository extends JpaRepository<EmotionLogJpaEntity, Long> {

    List<EmotionLogJpaEntity> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime after);

    @Query("SELECT e FROM EmotionLogJpaEntity e WHERE e.userId = :userId " +
           "AND (:since IS NULL OR e.createdAt >= :since) " +
           "ORDER BY e.createdAt DESC")
    List<EmotionLogJpaEntity> findRecent(@Param("userId") UUID userId,
                                          @Param("since") LocalDateTime since,
                                          Pageable page);
}
