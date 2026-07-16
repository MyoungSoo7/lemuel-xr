package github.lms.lemuel.xr.scripture.adapter.`in`.web

import github.lms.lemuel.xr.scripture.application.GetScripturePassageUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 본문 조회 — DB lookup, LLM 거치지 않음. RAG hallucination 가드레일.
 *
 * - GET /api/scripture/{ref} — 단일 본문 (translation 옵션)
 * - GET /api/scripture/range — 책·장 범위 본문
 * - POST /api/scripture/search — pgvector 의미 검색 (Phase 2 — 임베딩 준비된 본문만)
 *
 * use-case 는 도메인 모델([github.lms.lemuel.xr.scripture.domain.ScripturePassage])을 반환하고,
 * 컨트롤러가 이를 DTO 로 변환해 응답한다. JPA 엔티티는 영속성 어댑터 안쪽에 갇혀 웹 경계를 넘지 않는다.
 */
@RestController
@RequestMapping("/api/scripture")
class ScriptureController(
    private val getScripturePassage: GetScripturePassageUseCase,
) {

    @GetMapping("/{ref}")
    fun byRef(
        @PathVariable("ref") reference: String,
        @RequestParam(value = "translation", defaultValue = "modern") translation: String,
    ): ResponseEntity<ScripturePassageDto> =
        ResponseEntity.ok(
            ScripturePassageDto.from(getScripturePassage.byReference(reference, translation)),
        )

    @GetMapping("/range")
    fun range(
        @RequestParam book: String,
        @RequestParam chapter: Int,
        @RequestParam(name = "from") verseFrom: Int,
        @RequestParam(name = "to") verseTo: Int,
        @RequestParam(value = "translation", defaultValue = "modern") translation: String,
    ): ResponseEntity<RangeResponse> {
        val passages = getScripturePassage.range(book, chapter, verseFrom, verseTo, translation)
            .map(ScripturePassageDto::from)
        return ResponseEntity.ok(
            RangeResponse(book, chapter, verseFrom, verseTo, translation, passages),
        )
    }

    @PostMapping("/search")
    fun search(@RequestBody req: SearchRequest): ResponseEntity<SearchResponse> {
        val matches = getScripturePassage.search(req.query, req.limit)
            .map(ScripturePassageDto::from)
        return ResponseEntity.ok(SearchResponse(req.query, matches))
    }

    data class RangeResponse(
        val book: String,
        val chapter: Int,
        val verseFrom: Int,
        val verseTo: Int,
        val translation: String,
        val passages: List<ScripturePassageDto>,
    )

    data class SearchRequest(val query: String, val limit: Int?)

    data class SearchResponse(val query: String, val matches: List<ScripturePassageDto>)
}
