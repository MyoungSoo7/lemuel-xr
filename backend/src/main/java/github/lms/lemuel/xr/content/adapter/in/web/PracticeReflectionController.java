package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.content.adapter.out.persistence.PracticeReflectionJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.PracticeReflectionRepository;
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner;
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/content/practice — Theme 6 (마음 지킴, 잠 4:23) · 7 (사람 두려움, 잠 29:25/사 51:7)
 * 실천/성찰 기록.
 *
 * <p>TRACK-A-5-7-ACTION-GUIDANCE §3·§4·§7. JournalController 스타일 (controller + repo).
 *
 * <p><b>안전선 (R1~R5)</b>:
 * <ul>
 *   <li>R1 — 사용자 자유 기록(situation)을 CrisisKeywordScanner 로 스캔.
 *       자해·자살 키워드 매칭 시 RecordSafetyAlertUseCase 로 safety_alert INSERT +
 *       위기 자원(1393 등) 반환. 응답 {@code crisis.routed=true} → 프론트가 일기(#1)/위기 카드로 라우팅.
 *       *법적 의무 — 완화·제거 금지.*</li>
 *   <li>R2 — 응답 {@code safetyFooter} 로 피해 상황 보호 문구 항상 동반.</li>
 *   <li>footer — "AI 보조 — 본문은 성경 참조" (성경 외 근거 없음).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/content/practice")
@RequiredArgsConstructor
@Validated
public class PracticeReflectionController {

    private final PracticeReflectionRepository repo;
    private final CrisisKeywordScanner crisisScanner;
    private final RecordSafetyAlertUseCase recordSafetyAlert;

    /** "AI 보조 — 본문은 성경 참조" (§10 출시 전 체크리스트). */
    private static final String AI_FOOTER = "AI 보조 — storyteller, 본문은 성경 참조";

    /**
     * Theme 6/7 실천 기록 저장. situation 은 R1 키워드 스캔 통과.
     */
    @PostMapping
    public ResponseEntity<PracticeResponse> create(@RequestBody CreateRequest req) {
        UUID userId = RequestContext.currentUserId();

        // R1 (법적 의무): 사용자 자유 기록에 자해·자살 키워드가 있으면 즉시 위기 자원 라우팅.
        CrisisKeywordScanner.ScanResult scan = crisisScanner.scan(req.situation());
        RecordSafetyAlertUseCase.Result alert = recordSafetyAlert.execute(
                userId, null, "practice_" + req.topicId(), scan);

        PracticeReflectionJpaEntity e = new PracticeReflectionJpaEntity();
        e.setId(null);
        e.setUserId(userId);
        e.setTopicId(req.topicId());
        e.setPracticeKind(req.practiceKind());
        e.setSituation(req.situation());
        e.setReflection(req.reflection());
        e.setActionTaken(Boolean.TRUE.equals(req.actionTaken()));
        e.setScriptureRef(req.scriptureRef());
        e.setDimension(req.dimension());
        e.setCreatedAt(LocalDateTime.now());
        repo.save(e);

        return ResponseEntity.ok(new PracticeResponse(
                toDto(e),
                new CrisisRouting(alert.triggered(), alert.shownResources()),
                safetyFooter(req.topicId()),
                AI_FOOTER
        ));
    }

    /** 주제별 실천 이력 + 누적 행동 카운트 (§7 "행동 자유도" 신호). */
    @GetMapping
    public ResponseEntity<PracticeListResponse> list(
            @RequestParam @Min(6) @Max(7) short topicId,
            @RequestParam(defaultValue = "20") int limit) {
        UUID userId = RequestContext.currentUserId();
        List<PracticeDto> items = repo
                .findByUserIdAndTopicIdOrderByCreatedAtDesc(userId, topicId,
                        PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(this::toDto).toList();
        long actionCount = repo.countByUserIdAndTopicIdAndActionTakenTrue(userId, topicId);
        return ResponseEntity.ok(new PracticeListResponse(topicId, items, actionCount));
    }

    /** R2 — 피해 상황 사용자 보호 footer. Theme 별 문구 (§3.4 / §4.4). */
    private String safetyFooter(short topicId) {
        if (topicId == 6) {
            return "마음 지킴은 가해자를 보호하는 도구가 아닙니다. 안전이 우선입니다."
                    + " 피해 상황에 있는 분도 이 묵상에 안전하게 머물 수 있게 자원을 함께 안내합니다."
                    + " (1577-0199 자살예방 · 1393 생명의전화 · 24시간)";
        }
        // topicId == 7
        return "두려움은 실재 위협의 신호일 수 있습니다. 안전 확보가 우선입니다."
                + " 피해 상황에 있는 분도 이 묵상에 안전하게 머물 수 있게 자원을 함께 안내합니다."
                + " (1577-0199 자살예방 · 1393 생명의전화 · 24시간)";
    }

    private PracticeDto toDto(PracticeReflectionJpaEntity e) {
        return new PracticeDto(e.getId(), e.getTopicId(), e.getPracticeKind(),
                e.getSituation(), e.getReflection(), e.getActionTaken(),
                e.getScriptureRef(), e.getDimension(), e.getCreatedAt());
    }

    // --- DTOs ---

    public record CreateRequest(
            @NotNull @Min(6) @Max(7) Short topicId,
            @NotNull @Size(max = 30) String practiceKind,
            @Size(max = 5000) String situation,
            Map<String, Object> reflection,
            Boolean actionTaken,
            @Size(max = 50) String scriptureRef,
            @Size(max = 20) String dimension
    ) {}

    public record PracticeDto(Long id, Short topicId, String practiceKind,
                               String situation, Map<String, Object> reflection,
                               Boolean actionTaken, String scriptureRef,
                               String dimension, LocalDateTime createdAt) {}

    /** R1 — 위기 키워드 매칭 시 프론트가 일기(#1)/위기 카드로 라우팅. */
    public record CrisisRouting(boolean routed, List<Map<String, Object>> resources) {}

    public record PracticeResponse(PracticeDto practice, CrisisRouting crisis,
                                    String safetyFooter, String aiFooter) {}

    public record PracticeListResponse(short topicId, List<PracticeDto> items, long actionCount) {}
}
