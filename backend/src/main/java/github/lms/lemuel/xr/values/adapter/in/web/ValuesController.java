package github.lms.lemuel.xr.values.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.values.application.GetValueProfileUseCase;
import github.lms.lemuel.xr.values.application.RecordPracticeUseCase;
import github.lms.lemuel.xr.values.application.UpdateValueProfileUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/values/* — 자기만의 7 가치 빌더 (CROSS-MAPPING-VR-AR.md §5).
 *
 * <p>4 endpoint:
 * <ul>
 *   <li>GET  /me        — 본인 프로파일 + 최근 7일 통계 + CDR Index</li>
 *   <li>POST /profile   — 7 가치 정의 (부분 갱신)</li>
 *   <li>POST /practice  — 오늘의 실천 1건 기록</li>
 *   <li>GET  /streak    — TODO Phase 2 — 연속 실천일</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/values")
@RequiredArgsConstructor
@Validated
public class ValuesController {

    private final GetValueProfileUseCase getProfileUc;
    private final UpdateValueProfileUseCase updateProfileUc;
    private final RecordPracticeUseCase recordPracticeUc;

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me() {
        var r = getProfileUc.execute(RequestContext.currentUserId());
        return ResponseEntity.ok(new ProfileResponse(
                r.valuesJson(),
                new StatsDto(r.totalPractices7d(), r.countByValue(), r.cdrIndex(), r.tier())
        ));
    }

    @PostMapping("/profile")
    public ResponseEntity<ProfileResponse> upsert(@RequestBody @NotNull Map<String, Object> patch) {
        var p = updateProfileUc.execute(RequestContext.currentUserId(), patch);
        var stats = getProfileUc.execute(RequestContext.currentUserId());
        return ResponseEntity.ok(new ProfileResponse(
                p.getValuesJson(),
                new StatsDto(stats.totalPractices7d(), stats.countByValue(), stats.cdrIndex(), stats.tier())
        ));
    }

    @PostMapping("/practice")
    public ResponseEntity<PracticeDto> practice(@RequestBody PracticeRequest req) {
        var p = recordPracticeUc.execute(
                RequestContext.currentUserId(),
                req.valueId(),
                req.durationSec(),
                req.note(),
                req.linkedCharacter(),
                req.linkedGameSession()
        );
        return ResponseEntity.ok(new PracticeDto(
                p.getId(), p.getValueId(), p.getPracticedAt(),
                p.getDurationSec(), p.getNote(),
                p.getLinkedCharacter(), p.getLinkedGameSession()
        ));
    }

    // --- DTOs ---
    public record ProfileResponse(Map<String, Object> values, StatsDto stats) {}
    public record StatsDto(int totalPractices7d, Map<String, Integer> countByValue,
                            int cdrIndex, String tier) {}

    public record PracticeRequest(
            @NotNull @Min(1) @Max(7) Integer valueId,
            Integer durationSec,
            String note,
            String linkedCharacter,
            UUID linkedGameSession
    ) {}

    public record PracticeDto(Long id, Short valueId, OffsetDateTime practicedAt,
                                Integer durationSec, String note,
                                String linkedCharacter, UUID linkedGameSession) {}
}
