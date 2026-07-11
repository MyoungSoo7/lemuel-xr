package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.content.adapter.out.persistence.EcclesiastesViewJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.EcclesiastesViewRepository;
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner;
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase;
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
 * /api/content/ecclesiastes — 기준4 (전도서와 인생).
 *
 * <p>TRACK-A-1-4-WISDOM-EMOTION §4 (Theme 3). PracticeReflectionController 스타일 (controller + repo).
 * 인생에 관한 전도서의 진리를 <b>카테고리로 분류</b>해 보여주고, 사용자가 자신의 '인생 계절'(user_season)
 * 에 맞춰 헛됨(futility)과 의미(meaning)를 성찰·기록한다. ecclesiastes_views 테이블 사용.
 *
 * <p><b>근거는 성경만</b> — 카테고리·계절·성구는 전도서 + 관련 성구뿐. 성경 외 자료 배제.
 *
 * <p><b>안전선 (R1~R5)</b>:
 * <ul>
 *   <li>R1 — futility_note / meaning_note 를 CrisisKeywordScanner 로 스캔. 자해·자살 키워드 매칭 시
 *       RecordSafetyAlertUseCase 로 safety_alert INSERT + 위기 자원 반환. 응답 {@code crisis.routed=true}
 *       → 프론트가 일기(#1)/위기 카드로 라우팅. 전도서는 nihilism 을 강화할 수 있어(§4.4) 스캔이 특히 중요.
 *       *법적 의무 — 완화·제거 금지.*</li>
 *   <li>R2 — 전도서의 '헛됨'을 절망이 아니라 "해 아래 유한함의 정직한 인정 → 창조주 경외(전 12:13)"로
 *       마무리하는 톤. {@code safetyFooter} + {@code conclusionInvite} 로 항상 결론까지 안내. R3 압박 배제.</li>
 *   <li>footer — "AI 보조 — 본문은 성경 참조" (성경 외 근거 없음).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/content/ecclesiastes")
@RequiredArgsConstructor
@Validated
public class EcclesiastesController {

    private final EcclesiastesViewRepository repo;
    private final CrisisKeywordScanner crisisScanner;
    private final RecordSafetyAlertUseCase recordSafetyAlert;

    private static final String AI_FOOTER = "AI 보조 — 본문은 성경 참조. 전도서와 관련 성구 외 자료는 사용하지 않습니다.";

    /**
     * §4.4 결론 회피 금지 — '헛됨'만 노출하지 않고 창조주 경외(전 12:13)로 마무리하는 안내 문구.
     * 절망이 아니라 "해 아래 유한함의 정직한 인정" 톤 (R2). 고난 미화·방치로 미끄러지지 않게.
     */
    private static final String SAFETY_FOOTER =
            "전도서의 \"헛됨\"은 인생을 포기하라는 말이 아니라, 해 아래 유한함을 정직하게 인정하는 자리입니다."
                    + " 솔로몬은 그 인정을 절망이 아니라 창조주를 경외하는 결론으로 마무리합니다"
                    + " — \"일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다\" (전 12:13)."
                    + " 무엇을 더 해내야 한다는 압박 없이, 오늘의 유한함을 그대로 하나님 앞에 두어도 괜찮습니다.";

    /** §4.4 — 응답에 항상 동반해 전 12:13 결론까지 함께 보도록 초대. */
    private static final String CONCLUSION_INVITE =
            "1~11장의 헛됨만 보고 멈추지 마시고, 전 12:13~14 결론까지 함께 보실 수 있습니다.";

    /**
     * 인생에 관한 전도서 진리 — 카테고리 분류 조회.
     *
     * <p>사용자의 '인생 계절'을 고를 수 있게 카테고리별 전도서 본문(chapter_ref)과 성구를 함께 제공.
     * 성경만 근거 — 정적 상수(EcclesiastesCatalog). DB 시드 불필요.
     */
    @GetMapping("/categories")
    public ResponseEntity<CategoryListResponse> categories() {
        return ResponseEntity.ok(new CategoryListResponse(
                EcclesiastesCatalog.CATEGORIES, EcclesiastesCatalog.SEASONS, AI_FOOTER));
    }

    /**
     * 사용자 view 기록. futility/meaning 자유 기록은 R1 키워드 스캔 통과.
     */
    @PostMapping
    public ResponseEntity<EcclesiastesResponse> create(@RequestBody CreateRequest req) {
        UUID userId = RequestContext.currentUserId();

        // R1 (법적 의무): 헛됨/의미 자유 기록 양쪽 모두 스캔. 전도서 특성상 nihilism 강화 위험(§4.4).
        CrisisKeywordScanner.ScanResult scan = crisisScanner.scan(joinNotes(req.futilityNote(), req.meaningNote()));
        RecordSafetyAlertUseCase.Result alert = recordSafetyAlert.execute(
                userId, null, "ecclesiastes", scan);

        EcclesiastesViewJpaEntity e = new EcclesiastesViewJpaEntity();
        e.setId(null);
        e.setUserId(userId);
        e.setChapterRef(req.chapterRef());
        e.setUserSeason(req.userSeason());
        e.setFutilityNote(req.futilityNote());
        e.setMeaningNote(req.meaningNote());
        e.setListenedAudio(Boolean.TRUE.equals(req.listenedAudio()));
        e.setConclusionViewed(Boolean.TRUE.equals(req.conclusionViewed()));
        e.setCreatedAt(LocalDateTime.now());
        repo.save(e);

        return ResponseEntity.ok(new EcclesiastesResponse(
                toDto(e),
                new CrisisRouting(alert.triggered(), alert.shownResources()),
                SAFETY_FOOTER,
                CONCLUSION_INVITE,
                AI_FOOTER
        ));
    }

    /** 본인 전도서 성찰 이력 + 결론(전 12:13)까지 본 누적 수(§4.4 신호). */
    @GetMapping
    public ResponseEntity<EcclesiastesListResponse> list(
            @RequestParam(defaultValue = "20") int limit) {
        UUID userId = RequestContext.currentUserId();
        List<EcclesiastesDto> items = repo
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(this::toDto).toList();
        long conclusionCount = repo.countByUserIdAndConclusionViewedTrue(userId);
        return ResponseEntity.ok(new EcclesiastesListResponse(items, conclusionCount, CONCLUSION_INVITE));
    }

    private static String joinNotes(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + "\n" + b;
    }

    private EcclesiastesDto toDto(EcclesiastesViewJpaEntity e) {
        return new EcclesiastesDto(e.getId(), e.getChapterRef(), e.getUserSeason(),
                e.getFutilityNote(), e.getMeaningNote(), e.getListenedAudio(),
                e.getConclusionViewed(), e.getCreatedAt());
    }

    // --- DTOs ---

    public record CreateRequest(
            @Size(max = 20) String chapterRef,
            @Size(max = 20) String userSeason,
            @Size(max = 5000) String futilityNote,
            @Size(max = 5000) String meaningNote,
            Boolean listenedAudio,
            Boolean conclusionViewed
    ) {}

    public record EcclesiastesDto(Long id, String chapterRef, String userSeason,
                                   String futilityNote, String meaningNote,
                                   Boolean listenedAudio, Boolean conclusionViewed,
                                   LocalDateTime createdAt) {}

    /** R1 — 위기 키워드 매칭 시 프론트가 일기(#1)/위기 카드로 라우팅. */
    public record CrisisRouting(boolean routed, List<Map<String, Object>> resources) {}

    public record EcclesiastesResponse(EcclesiastesDto view, CrisisRouting crisis,
                                        String safetyFooter, String conclusionInvite, String aiFooter) {}

    public record EcclesiastesListResponse(List<EcclesiastesDto> items, long conclusionViewedCount,
                                            String conclusionInvite) {}

    public record CategoryListResponse(List<EcclesiastesCatalog.Category> categories,
                                        List<EcclesiastesCatalog.Season> seasons, String aiFooter) {}
}
