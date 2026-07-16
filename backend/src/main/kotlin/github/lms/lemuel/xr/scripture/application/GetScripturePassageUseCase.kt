package github.lms.lemuel.xr.scripture.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.scripture.application.port.out.ScripturePassagePort
import github.lms.lemuel.xr.scripture.domain.ScripturePassage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 본문 조회 use-case — DB lookup, LLM 거치지 않음. RAG hallucination 가드레일.
 *
 * 영속성 포트([ScripturePassagePort])에만 의존한다. 웹 경계로의 DTO 변환은 컨트롤러 책임.
 */
@Service
class GetScripturePassageUseCase(
    private val passages: ScripturePassagePort,
) {

    /** 단일 본문 (translation 옵션). */
    @Transactional(readOnly = true)
    fun byReference(reference: String, translation: String): ScripturePassage =
        passages.findFirstByReferenceAndTranslation(reference, translation)
            .orElseThrow {
                AppException(
                    ErrorCode.E_SCRIPTURE_NOT_FOUND,
                    "ref=$reference translation=$translation",
                )
            }

    /** 책·장 범위 본문. */
    @Transactional(readOnly = true)
    fun range(
        book: String,
        chapter: Int,
        verseFrom: Int,
        verseTo: Int,
        translation: String,
    ): List<ScripturePassage> =
        passages.findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
            book.lowercase(), chapter, translation, verseFrom, verseTo,
        )

    /**
     * 의미 검색 — MVP 는 keyword LIKE fallback (임베딩 미생성 본문 호환).
     *
     * pgvector 의미 검색은 Phase 2 에서 임베딩 포트를 도입해 처리 예정.
     */
    @Transactional(readOnly = true)
    fun search(query: String, limit: Int?): List<ScripturePassage> {
        val capped = if (limit == null) 5 else minOf(limit, 50)
        return passages.searchByTextLike("%$query%", capped)
    }
}
