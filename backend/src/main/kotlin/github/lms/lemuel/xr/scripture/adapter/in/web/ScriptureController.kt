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
 *
 * 기본 번역본은 `rev`(개역개정) 다. 2026-08-21 에 `modern`(현대인의 성경) 에서 옮겼다 —
 * 프론트 모놀로그가 개역개정 자구인데 본문 API 는 KLB 를 내려 보내 **한 화면에 같은 절이
 * 두 자구로 뜨고 있었다**. 스키마(`V1__init_schema.sql:46`)와 두 시드 마이그레이션이 처음부터
 * 예고해 둔 swap 이다(라이선스: `docs/BACKEND-ARCHITECTURE.md:847` 위험 #4).
 *
 * ⚠️ 이 기본값을 바꾸려면 **그 번역본 행이 먼저 있어야 한다.** [GetScripturePassageUseCase] 는
 * 폴백 없이 던지므로(`E_SCRIPTURE_NOT_FOUND`), 행이 없는 라벨로 바꾸는 순간 전 참조가 404 다.
 * `V20260821030000` 이 rev 92행을 modern 과 같은 참조 집합으로 채우고, 그 마이그레이션의
 * DO 블록이 "rev 가 못 덮는 참조" 를 0 으로 강제한다.
 */
@RestController
@RequestMapping("/api/scripture")
class ScriptureController(
    private val getScripturePassage: GetScripturePassageUseCase,
) {

    @GetMapping("/{ref}")
    fun byRef(
        @PathVariable("ref") reference: String,
        @RequestParam(value = "translation", defaultValue = "rev") translation: String,
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
        @RequestParam(value = "translation", defaultValue = "rev") translation: String,
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
