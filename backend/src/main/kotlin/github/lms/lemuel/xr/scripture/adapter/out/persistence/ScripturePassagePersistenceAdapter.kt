package github.lms.lemuel.xr.scripture.adapter.out.persistence

import github.lms.lemuel.xr.scripture.application.port.out.ScripturePassagePort
import github.lms.lemuel.xr.scripture.domain.ScripturePassage
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * [ScripturePassagePort] 구현 — Spring Data [ScripturePassageJpaRepository] 위임.
 *
 * [ScripturePassageEntity] 를 [ScripturePassage] 도메인 모델로 매핑하는 유일한 지점.
 * 이 클래스 밖으로는 엔티티가 새어 나가지 않는다.
 */
@Component
class ScripturePassagePersistenceAdapter(
    private val repository: ScripturePassageJpaRepository,
) : ScripturePassagePort {

    override fun findFirstByReferenceAndTranslation(
        reference: String,
        translation: String,
    ): Optional<ScripturePassage> =
        repository.findFirstByReferenceAndTranslation(reference, translation)
            .map(::toDomain)

    override fun findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
        bookCode: String,
        chapter: Int,
        translation: String,
        verseFrom: Int,
        verseTo: Int,
    ): List<ScripturePassage> =
        repository.findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
            bookCode, chapter, translation, verseFrom, verseTo,
        ).map(::toDomain)

    override fun searchByTextLike(pattern: String, limit: Int): List<ScripturePassage> =
        repository.searchByTextLike(pattern, PageRequest.of(0, limit))
            .map(::toDomain)

    private fun toDomain(e: ScripturePassageEntity): ScripturePassage =
        ScripturePassage(
            id = e.id,
            reference = e.reference!!,
            translation = e.translation!!,
            book = e.book!!,
            bookCode = e.bookCode,
            chapter = e.chapter!!,
            verseStart = e.verseStart!!,
            verseEnd = e.verseEnd,
            text = e.text!!,
            themeTags = e.themeTags,
            characterTags = e.characterTags,
        )
}
