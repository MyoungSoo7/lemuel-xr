package github.lms.lemuel.xr.tts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TtsCacheRepository extends JpaRepository<TtsCacheJpaEntity, String> {
}
