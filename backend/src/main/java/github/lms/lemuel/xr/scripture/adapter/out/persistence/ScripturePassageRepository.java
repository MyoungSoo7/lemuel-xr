package github.lms.lemuel.xr.scripture.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScripturePassageRepository extends JpaRepository<ScripturePassageEntity, Long> {
    Optional<ScripturePassageEntity> findFirstByReferenceAndTranslation(String reference, String translation);

    List<ScripturePassageEntity> findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
            String bookCode, int chapter, String translation, int verseFrom, int verseTo);

    @Query("SELECT s FROM ScripturePassageEntity s WHERE s.text LIKE :pattern ORDER BY s.id")
    List<ScripturePassageEntity> searchByTextLike(@Param("pattern") String pattern, Pageable page);
}
