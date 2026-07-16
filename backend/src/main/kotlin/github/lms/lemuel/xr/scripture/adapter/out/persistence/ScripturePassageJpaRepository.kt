package github.lms.lemuel.xr.scripture.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ScripturePassageJpaRepository : JpaRepository<ScripturePassageEntity, Long> {

    fun findFirstByReferenceAndTranslation(reference: String, translation: String): Optional<ScripturePassageEntity>

    fun findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
        bookCode: String,
        chapter: Int,
        translation: String,
        verseFrom: Int,
        verseTo: Int,
    ): List<ScripturePassageEntity>

    @Query("SELECT s FROM ScripturePassageEntity s WHERE s.text LIKE :pattern ORDER BY s.id")
    fun searchByTextLike(@Param("pattern") pattern: String, page: Pageable): List<ScripturePassageEntity>
}
