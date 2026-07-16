package github.lms.lemuel.xr.scripture.application.port.out

import github.lms.lemuel.xr.scripture.domain.ScripturePassage
import java.util.Optional

/**
 * ScripturePassage 영속성 아웃바운드 포트.
 *
 * Interface Segregation — 앱에서 실제 호출하는 메서드만 선언한다 (full CRUD 슈퍼셋 아님).
 *
 * 포트는 도메인 모델([ScripturePassage])만 주고받는다. JPA 엔티티는 어댑터 안쪽에 갇힌다.
 */
interface ScripturePassagePort {

    fun findFirstByReferenceAndTranslation(reference: String, translation: String): Optional<ScripturePassage>

    fun findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
        bookCode: String,
        chapter: Int,
        translation: String,
        verseFrom: Int,
        verseTo: Int,
    ): List<ScripturePassage>

    fun searchByTextLike(pattern: String, limit: Int): List<ScripturePassage>
}
