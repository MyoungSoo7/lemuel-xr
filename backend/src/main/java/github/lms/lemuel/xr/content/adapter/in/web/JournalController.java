package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.content.adapter.out.persistence.DiaryEntryJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.DiaryEntryRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** /api/content/journal — Theme 1 일기. */
@RestController
@RequestMapping("/api/content/journal")
@RequiredArgsConstructor
@Validated
public class JournalController {

    private final DiaryEntryRepository repo;

    @PostMapping
    public ResponseEntity<JournalDto> create(@RequestBody CreateRequest req) {
        LocalDateTime now = LocalDateTime.now();
        DiaryEntryJpaEntity e = new DiaryEntryJpaEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(RequestContext.currentUserId());
        e.setBody(req.text());
        e.setFormType(req.formType());
        e.setEmotionLabel(req.emotionLabel());
        e.setIntensity(req.intensity());
        e.setWordCount(req.text() == null ? 0 : req.text().split("\\s+").length);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        repo.save(e);
        return ResponseEntity.ok(toDto(e));
    }

    @GetMapping
    public ResponseEntity<JournalListResponse> list(@RequestParam(defaultValue = "20") int limit) {
        List<JournalDto> items = repo
                .findByUserIdOrderByCreatedAtDesc(RequestContext.currentUserId(), PageRequest.of(0, limit))
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(new JournalListResponse(items));
    }

    private JournalDto toDto(DiaryEntryJpaEntity e) {
        return new JournalDto(e.getId(), e.getBody(), e.getFormType(),
                e.getEmotionLabel(), e.getIntensity(), e.getWordCount(),
                e.getMeditationText(), e.getMeditationAccepted(),
                e.getCreatedAt());
    }

    public record CreateRequest(
            @NotBlank @Size(max = 5000) String text,
            @Size(max = 20) String formType,
            @Size(max = 30) String emotionLabel,
            Short intensity
    ) {}

    public record JournalDto(UUID id, String text, String formType, String emotionLabel,
                              Short intensity, Integer wordCount, String meditationText,
                              Boolean meditationAccepted, LocalDateTime createdAt) {}

    public record JournalListResponse(List<JournalDto> items) {}
}
