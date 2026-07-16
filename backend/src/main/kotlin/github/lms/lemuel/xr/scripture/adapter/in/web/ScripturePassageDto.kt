package github.lms.lemuel.xr.scripture.adapter.`in`.web

import github.lms.lemuel.xr.scripture.domain.ScripturePassage

/**
 * 본문 응답 DTO — 도메인 모델([ScripturePassage])을 웹 경계 표현으로 변환하는 방어막.
 *
 * 필드명은 기존 직렬화 JSON 과 동일하게 유지한다.
 */
data class ScripturePassageDto(
    val id: Long?,
    val reference: String,
    val translation: String,
    val book: String,
    val bookCode: String?,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int?,
    val text: String,
    val themeTags: Array<String>?,
    val characterTags: Array<String>?,
) {
    companion object {
        fun from(p: ScripturePassage): ScripturePassageDto =
            ScripturePassageDto(
                id = p.id,
                reference = p.reference,
                translation = p.translation,
                book = p.book,
                bookCode = p.bookCode,
                chapter = p.chapter,
                verseStart = p.verseStart,
                verseEnd = p.verseEnd,
                text = p.text,
                themeTags = p.themeTags,
                characterTags = p.characterTags,
            )
    }
}
