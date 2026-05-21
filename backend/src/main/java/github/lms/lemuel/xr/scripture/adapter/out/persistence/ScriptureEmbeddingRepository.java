package github.lms.lemuel.xr.scripture.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptureEmbeddingRepository extends JpaRepository<ScriptureEmbeddingJpaEntity, Long> {
}
