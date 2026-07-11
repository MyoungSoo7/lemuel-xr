package github.lms.lemuel.xr.scripture.adapter.in.web;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.scripture.adapter.out.persistence.ScripturePassageEntity;
import github.lms.lemuel.xr.scripture.adapter.out.persistence.ScripturePassageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 본문 조회 — DB lookup, LLM 거치지 않음. RAG hallucination 가드레일.
 *
 * <ul>
 *   <li>GET /api/scripture/{ref} — 단일 본문 (translation 옵션)</li>
 *   <li>GET /api/scripture/range — 책·장 범위 본문</li>
 *   <li>POST /api/scripture/search — pgvector 의미 검색 (Phase 2 — 임베딩 준비된 본문만)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/scripture")
@RequiredArgsConstructor
public class ScriptureController {

    private final ScripturePassageRepository repo;

    @GetMapping("/{ref}")
    public ResponseEntity<ScripturePassageEntity> byRef(
            @PathVariable("ref") String reference,
            @RequestParam(value = "translation", defaultValue = "modern") String translation) {
        return repo.findFirstByReferenceAndTranslation(reference, translation)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new AppException(ErrorCode.E_SCRIPTURE_NOT_FOUND,
                        "ref=" + reference + " translation=" + translation));
    }

    @GetMapping("/range")
    public ResponseEntity<RangeResponse> range(
            @RequestParam String book,
            @RequestParam int chapter,
            @RequestParam(name = "from") int verseFrom,
            @RequestParam(name = "to") int verseTo,
            @RequestParam(value = "translation", defaultValue = "modern") String translation) {
        List<ScripturePassageEntity> passages = repo.findByBookCodeAndChapterAndTranslationAndVerseStartBetween(
                book.toLowerCase(), chapter, translation, verseFrom, verseTo);
        return ResponseEntity.ok(new RangeResponse(book, chapter, verseFrom, verseTo, translation, passages));
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest req) {
        // pgvector 의미 검색은 ScriptureEmbeddingRepository 가 처리.
        // MVP: keyword LIKE fallback (임베딩 미생성 본문 호환).
        int limit = req.limit() == null ? 5 : Math.min(req.limit(), 50);
        List<ScripturePassageEntity> matches = repo.searchByTextLike(
                "%" + req.query() + "%", PageRequest.of(0, limit));
        return ResponseEntity.ok(new SearchResponse(req.query(), matches));
    }

    public record RangeResponse(String book, int chapter, int verseFrom, int verseTo,
                                  String translation, List<ScripturePassageEntity> passages) {}

    public record SearchRequest(String query, Integer limit) {}

    public record SearchResponse(String query, List<ScripturePassageEntity> matches) {}
}
