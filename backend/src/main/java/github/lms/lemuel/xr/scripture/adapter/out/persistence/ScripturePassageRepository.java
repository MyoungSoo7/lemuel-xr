package github.lms.lemuel.xr.scripture.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScripturePassageRepository extends JpaRepository<ScripturePassageEntity, Long> {
    Optional<ScripturePassageEntity> findFirstByReferenceAndTranslation(String reference, String translation);
}
