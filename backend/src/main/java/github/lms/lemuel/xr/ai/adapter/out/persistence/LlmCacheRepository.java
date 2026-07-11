package github.lms.lemuel.xr.ai.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCacheRepository extends JpaRepository<LlmCacheJpaEntity, String> {
}
