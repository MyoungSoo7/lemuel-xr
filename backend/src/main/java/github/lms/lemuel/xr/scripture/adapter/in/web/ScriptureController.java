package github.lms.lemuel.xr.scripture.adapter.in.web;

import github.lms.lemuel.xr.scripture.adapter.out.persistence.ScripturePassageEntity;
import github.lms.lemuel.xr.scripture.adapter.out.persistence.ScripturePassageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 본문 조회 — DB lookup, LLM 거치지 않음. RAG 가드레일.
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
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
