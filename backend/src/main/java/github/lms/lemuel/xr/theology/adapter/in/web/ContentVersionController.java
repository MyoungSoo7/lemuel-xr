package github.lms.lemuel.xr.theology.adapter.in.web;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentVersionJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/internal/content-versions/* — AI 생성 콘텐츠 관리.
 *
 * <p>X-Internal-Token 필요 (Python AI 사이드카가 생성 후 호출).
 * 상태 전이: draft → review → approved → published → archived.
 * 사용자 노출은 *published* 만.</p>
 */
@RestController
@RequestMapping("/api/internal/content-versions")
@RequiredArgsConstructor
public class ContentVersionController {

    private final ContentVersionRepository repo;

    @PostMapping
    public ResponseEntity<ContentVersionDto> create(@RequestBody CreateRequest req) {
        ContentVersionJpaEntity e = new ContentVersionJpaEntity();
        e.setId(UUID.randomUUID());
        e.setContentKind(req.contentKind());
        e.setContentRef(req.contentRef());
        e.setVersion(req.version());
        e.setBody(req.body());
        e.setStatus("draft");
        e.setGeneratedBy(req.generatedBy());
        e.setGenerationPrompt(req.generationPrompt());
        e.setCreatedAt(LocalDateTime.now());
        repo.save(e);
        return ResponseEntity.ok(toDto(e));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ContentVersionDto> updateStatus(@PathVariable UUID id,
                                                           @RequestBody StatusRequest req) {
        ContentVersionJpaEntity e = repo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.E_CONTENT_VERSION_NOT_FOUND));
        if (!isValidTransition(e.getStatus(), req.status())) {
            throw new AppException(ErrorCode.E_VALIDATION,
                    "Invalid transition: " + e.getStatus() + " -> " + req.status());
        }
        e.setStatus(req.status());
        if ("published".equals(req.status())) {
            e.setPublishedAt(LocalDateTime.now());
        }
        return ResponseEntity.ok(toDto(e));
    }

    @GetMapping("/{contentKind}/{contentRef}/published")
    public ResponseEntity<ContentVersionDto> latestPublished(@PathVariable String contentKind,
                                                              @PathVariable String contentRef) {
        return repo.findFirstByContentKindAndContentRefAndStatusOrderByPublishedAtDesc(
                        contentKind, contentRef, "published")
                .map(e -> ResponseEntity.ok(toDto(e)))
                .orElseThrow(() -> new AppException(ErrorCode.E_CONTENT_VERSION_NOT_FOUND));
    }

    private boolean isValidTransition(String from, String to) {
        return switch (from) {
            case "draft" -> "review".equals(to) || "archived".equals(to);
            case "review" -> "approved".equals(to) || "draft".equals(to) || "archived".equals(to);
            case "approved" -> "published".equals(to) || "archived".equals(to);
            case "published" -> "archived".equals(to);
            default -> false;
        };
    }

    private ContentVersionDto toDto(ContentVersionJpaEntity e) {
        return new ContentVersionDto(e.getId(), e.getContentKind(), e.getContentRef(),
                e.getVersion(), e.getStatus(), e.getBody(), e.getGeneratedBy(),
                e.getCreatedAt(), e.getPublishedAt());
    }

    public record CreateRequest(String contentKind, String contentRef, String version,
                                 Map<String, Object> body, String generatedBy,
                                 String generationPrompt) {}

    public record StatusRequest(String status) {}

    public record ContentVersionDto(UUID id, String contentKind, String contentRef,
                                      String version, String status,
                                      Map<String, Object> body, String generatedBy,
                                      LocalDateTime createdAt, LocalDateTime publishedAt) {}
}
