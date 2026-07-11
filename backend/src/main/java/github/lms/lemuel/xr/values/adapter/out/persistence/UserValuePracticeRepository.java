package github.lms.lemuel.xr.values.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserValuePracticeRepository extends JpaRepository<UserValuePracticeJpaEntity, Long> {

    @Query("SELECT p FROM UserValuePracticeJpaEntity p WHERE p.userId = :userId " +
           "AND p.practicedAt >= :since ORDER BY p.practicedAt DESC")
    List<UserValuePracticeJpaEntity> findRecent(@Param("userId") UUID userId,
                                                 @Param("since") OffsetDateTime since);

    @Query("SELECT p.valueId, COUNT(p) FROM UserValuePracticeJpaEntity p " +
           "WHERE p.userId = :userId AND p.practicedAt >= :since " +
           "GROUP BY p.valueId")
    List<Object[]> countByValue(@Param("userId") UUID userId,
                                 @Param("since") OffsetDateTime since);
}
