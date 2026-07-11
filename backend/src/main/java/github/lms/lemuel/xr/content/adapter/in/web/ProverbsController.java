package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.content.adapter.out.persistence.ProverbsInteractionJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.ProverbsInteractionRepository;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/content/proverbs — 기준2 (잠언과 지혜) 주제별 조회.
 *
 * <p>TRACK-A-1-4-WISDOM-EMOTION §3 (Theme 2). EcclesiastesController 스타일 (controller + catalog).
 * 잠언 지혜를 <b>카테고리(주제)로 분류</b>해 조회하고, 사용자가 고른 구절을
 * proverbs_interactions 테이블에 기록한다.
 *
 * <p><b>근거는 잠언(성경)만</b> — 주제·구절은 {@link ProverbsThemeCatalog} (실존 잠언)뿐.
 * 성경 외 자료 배제.
 *
 * <p><b>안전선 (R2/R3)</b>: 잠언의 단순화 위험(§3.4)을 피하기 위해 각 주제에 "결과 보장이 아니라 방향"
 * 안내를 동반. 정죄·회복 압박 배제. footer — "AI 보조 — 본문은 성경 참조".
 */
@RestController
@RequestMapping("/api/content/proverbs")
@RequiredArgsConstructor
@Validated
public class ProverbsController {

    private final ProverbsInteractionRepository repo;

    private static final String AI_FOOTER =
            "AI 보조 — 본문은 성경 참조. 잠언(성경) 외 자료는 사용하지 않습니다.";

    private static final String SAFETY_FOOTER =
            "잠언의 지혜는 결과를 보장하는 공식이 아니라 오늘 한 걸음의 방향입니다."
                    + " 지금 지쳐 있다면, 모든 지혜를 다 지켜야 한다는 압박 없이 마음에 닿는 한 구절만 붙잡으셔도 괜찮습니다.";

    /** 주제 목록 (프론트 주제 선택지). 잠언만 근거. */
    @GetMapping("/themes")
    public ResponseEntity<ThemeListResponse> themes() {
        return ResponseEntity.ok(new ThemeListResponse(
                ProverbsThemeCatalog.THEMES, SAFETY_FOOTER, AI_FOOTER));
    }

    /**
     * 주제(theme)로 잠언 구절 조회. 알 수 없는 theme 은 E_VALIDATION.
     */
    @GetMapping("/by-theme")
    public ResponseEntity<ByThemeResponse> byTheme(@RequestParam String theme) {
        ProverbsThemeCatalog.Theme t = ProverbsThemeCatalog.byKey(theme);
        if (t == null) {
            throw new AppException(ErrorCode.E_VALIDATION);
        }
        return ResponseEntity.ok(new ByThemeResponse(t, SAFETY_FOOTER, AI_FOOTER));
    }

    /**
     * 사용자가 고른 잠언 기록 — proverbs_interactions INSERT.
     * 기존 카드 추천 흐름과 동일 테이블 사용 (recommended_proverbs = 주제의 전체 구절).
     */
    @PostMapping("/interactions")
    public ResponseEntity<InteractionResponse> record(@RequestBody InteractionRequest req) {
        UUID userId = RequestContext.currentUserId();
        ProverbsThemeCatalog.Theme t = ProverbsThemeCatalog.byKey(req.theme());
        if (t == null) {
            throw new AppException(ErrorCode.E_VALIDATION);
        }

        ProverbsInteractionJpaEntity e = new ProverbsInteractionJpaEntity();
        e.setUserId(userId);
        e.setUserSituation(req.situation());
        e.setRecommendedProverbs(toProverbMaps(t));
        e.setChosenProverbRef(req.chosenProverbRef());
        e.setChosenDimension(req.dimension());
        e.setCreatedAt(LocalDateTime.now());
        repo.save(e);

        return ResponseEntity.ok(new InteractionResponse(
                e.getId(), t.key(), e.getChosenProverbRef(), SAFETY_FOOTER, AI_FOOTER));
    }

    /** 주제의 구절을 recommended_proverbs (기존 JSONB 형식 [{"ref":..,"text":..}]) 로 변환. */
    private List<Map<String, Object>> toProverbMaps(ProverbsThemeCatalog.Theme t) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProverbsThemeCatalog.Verse v : t.verses()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ref", v.ref());
            m.put("text", v.text());
            m.put("theme", t.key());
            out.add(m);
        }
        return out;
    }

    // --- DTOs ---

    public record InteractionRequest(
            @Size(max = 30) String theme,
            @Size(max = 2000) String situation,
            @Size(max = 20) String chosenProverbRef,
            @Size(max = 20) String dimension
    ) {}

    public record ThemeListResponse(List<ProverbsThemeCatalog.Theme> themes,
                                     String safetyFooter, String aiFooter) {}

    public record ByThemeResponse(ProverbsThemeCatalog.Theme theme,
                                   String safetyFooter, String aiFooter) {}

    public record InteractionResponse(Long id, String theme, String chosenProverbRef,
                                       String safetyFooter, String aiFooter) {}
}
