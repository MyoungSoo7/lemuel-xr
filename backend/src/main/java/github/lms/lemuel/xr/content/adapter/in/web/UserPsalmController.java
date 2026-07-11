package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.content.adapter.out.persistence.UserPsalmJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.UserPsalmRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** /api/content/psalms — Theme 4 사용자 시편 작성. */
@RestController
@RequestMapping("/api/content/psalms")
@RequiredArgsConstructor
@Validated
public class UserPsalmController {

    private final UserPsalmRepository repo;

    @PostMapping
    public ResponseEntity<PsalmDto> create(@RequestBody CreateRequest req) {
        UserPsalmJpaEntity p = new UserPsalmJpaEntity();
        p.setId(UUID.randomUUID());
        p.setUserId(RequestContext.currentUserId());
        p.setPsalmForm(req.form());
        p.setRawText(req.text());
        p.setInspiredByPsalm(req.inspiredBy());
        p.setCreatedAt(LocalDateTime.now());
        repo.save(p);
        return ResponseEntity.ok(toDto(p));
    }

    private PsalmDto toDto(UserPsalmJpaEntity p) {
        return new PsalmDto(p.getId(), p.getPsalmForm(), p.getRawText(),
                p.getPolishedText(), p.getAcceptedPolished(), p.getInspiredByPsalm(),
                p.getCreatedAt());
    }

    public record CreateRequest(
            @NotBlank @Size(max = 5000) String text,
            @Size(max = 20) String form,
            @Size(max = 20) String inspiredBy
    ) {}

    public record PsalmDto(UUID id, String form, String rawText, String polishedText,
                            Boolean acceptedPolished, String inspiredBy, LocalDateTime createdAt) {}
}
