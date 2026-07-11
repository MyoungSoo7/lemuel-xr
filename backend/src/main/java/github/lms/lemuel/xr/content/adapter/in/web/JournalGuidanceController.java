package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.emotion.domain.Emotion;
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner;
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/content/journal/guidance — 기준1 (일기와 묵상) 성경 기반 <b>규칙형 조언</b>.
 *
 * <p>TRACK-A-1-4-WISDOM-EMOTION §2 (Theme 1). JournalController + EcclesiastesController 스타일.
 * 일기에 대해 "상담/조언" 을 돌려주되 <b>LLM 을 붙이지 않고</b>(그건 별도 게이트),
 * 감정에 맞는 <b>성경 구절 + 성찰 질문 + (위기 시) 상담 자원</b> 을 정적 카탈로그
 * {@link JournalGuidanceCatalog} 로 반환한다.
 *
 * <p><b>근거는 성경만</b> — 구절·질문은 시편/잠언/복음서 등 성경 본문뿐. 성경 외 자료 배제.
 *
 * <p><b>안전선 (R1~R5)</b>:
 * <ul>
 *   <li>R1 — POST 의 일기 텍스트를 {@link CrisisKeywordScanner} 로 스캔. 자해·자살 키워드 매칭 시
 *       {@link RecordSafetyAlertUseCase} 로 safety_alert INSERT + 위기 자원 반환.
 *       응답 {@code crisis.routed=true} → 프론트가 일기(#1)/위기 카드로 라우팅.
 *       *법적 의무 — 완화·제거 금지.*</li>
 *   <li>R2/R3 — 조언은 "그 감정도 성경 안에 있다"는 인증(validation). 정죄·회복 압박 배제.
 *       성찰 질문은 답을 강요하지 않고 여는 형태.</li>
 *   <li>footer — {@code aiFooter} "AI 보조 — 본문은 성경 참조" (성경 외 근거 없음).</li>
 * </ul>
 *
 * <p>감정 감지: LLM 을 새로 붙이지 않으므로, POST 는 명시 {@code emotion} 우선 →
 * 없으면 룰 기반 키워드 감지 → 실패 시 CONFUSED fallback (Emotion.fromString 규칙과 동일).
 */
@RestController
@RequestMapping("/api/content/journal/guidance")
@RequiredArgsConstructor
@Validated
public class JournalGuidanceController {

    private final CrisisKeywordScanner crisisScanner;
    private final RecordSafetyAlertUseCase recordSafetyAlert;

    private static final String AI_FOOTER =
            "AI 보조 — 본문은 성경 참조. 성경(시편·잠언·복음서 등) 외 자료는 조언 근거로 쓰지 않습니다.";

    private static final String SAFETY_FOOTER =
            "이 조언은 성경 구절과 성찰 질문일 뿐, 의료·심리 진단이나 치료를 대체하지 않습니다."
                    + " 답을 강요하지 않습니다 — 오늘 마음에 닿는 한 구절, 한 질문만 붙잡으셔도 충분합니다.";

    /** 룰 기반 감정 감지용 키워드 (성경 조언 라우팅 목적). LLM 대체 — 단순 포함 매칭. */
    private static final Map<Emotion, List<String>> EMOTION_HINTS = Map.of(
            Emotion.ANXIOUS, List.of("불안", "걱정", "염려", "초조", "두려", "무서", "긴장"),
            Emotion.SAD, List.of("슬프", "슬픔", "우울", "눈물", "울", "상실", "낙심", "허탈"),
            Emotion.ANGRY, List.of("화", "분노", "짜증", "억울", "미워", "원망", "열받"),
            Emotion.CONFUSED, List.of("혼란", "모르겠", "헷갈", "갈피", "막막", "결정"),
            Emotion.LONELY, List.of("외로", "혼자", "고립", "쓸쓸", "그리워", "소외"),
            Emotion.EXHAUSTED, List.of("지침", "지쳐", "지친", "피곤", "번아웃", "힘들", "탈진", "소진"),
            Emotion.GRATEFUL, List.of("감사", "고마", "다행", "기쁨", "행복", "은혜"));

    /**
     * GET — 감정으로 성경 기반 조언 조회. 감정 미지정이면 전체 카탈로그(감정 선택지) 반환.
     * 카탈로그 조회이므로 위기 스캔 없음(입력 텍스트가 없음).
     */
    @GetMapping
    public ResponseEntity<GuidanceResponse> byEmotion(
            @RequestParam(required = false) String emotion) {
        if (emotion == null || emotion.isBlank()) {
            return ResponseEntity.ok(new GuidanceResponse(
                    null, JournalGuidanceCatalog.all(),
                    new CrisisRouting(false, List.of()),
                    SAFETY_FOOTER, AI_FOOTER));
        }
        JournalGuidanceCatalog.Guidance g = JournalGuidanceCatalog.forEmotion(Emotion.fromString(emotion));
        return ResponseEntity.ok(new GuidanceResponse(
                g, List.of(),
                new CrisisRouting(false, List.of()),
                SAFETY_FOOTER, AI_FOOTER));
    }

    /**
     * POST — 일기 텍스트 → 조언. R1: 텍스트를 위기 키워드 스캔 후, 감정 감지 →
     * 성경 구절 + 성찰 질문 반환. 위기 시 상담 자원 동반 + 일기(#1) 라우팅 신호.
     */
    @PostMapping
    public ResponseEntity<GuidanceResponse> fromText(@RequestBody GuidanceRequest req) {
        UUID userId = RequestContext.currentUserId();

        // R1 (법적 의무): 일기 텍스트에 자해·자살 키워드가 있으면 즉시 위기 자원 라우팅.
        CrisisKeywordScanner.ScanResult scan = crisisScanner.scan(req.text());
        RecordSafetyAlertUseCase.Result alert = recordSafetyAlert.execute(
                userId, null, "journal_guidance", scan);

        // 감정: 명시 우선 → 룰 기반 키워드 감지 → CONFUSED fallback (LLM 미사용).
        Emotion emotion = resolveEmotion(req.emotion(), req.text());
        JournalGuidanceCatalog.Guidance g = JournalGuidanceCatalog.forEmotion(emotion);

        // R1: 위기 매칭이면 카탈로그의 상담 자원을 함께(자원 record 형태 통일).
        List<Map<String, Object>> resources = alert.triggered()
                ? (alert.shownResources().isEmpty()
                        ? JournalGuidanceCatalog.CRISIS_RESOURCES : alert.shownResources())
                : List.of();

        return ResponseEntity.ok(new GuidanceResponse(
                g, List.of(),
                new CrisisRouting(alert.triggered(), resources),
                SAFETY_FOOTER, AI_FOOTER));
    }

    /** 명시 emotion 우선. 없으면 텍스트에서 룰 기반 키워드 감지. 매칭 없으면 CONFUSED. */
    private Emotion resolveEmotion(String explicit, String text) {
        if (explicit != null && !explicit.isBlank()) {
            return Emotion.fromString(explicit);
        }
        if (text == null || text.isBlank()) return Emotion.CONFUSED;
        for (var entry : EMOTION_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (text.contains(hint)) return entry.getKey();
            }
        }
        return Emotion.CONFUSED;
    }

    // --- DTOs ---

    public record GuidanceRequest(
            @Size(max = 5000) String text,
            @Size(max = 30) String emotion
    ) {}

    /** R1 — 위기 키워드 매칭 시 프론트가 일기(#1)/위기 카드로 라우팅. */
    public record CrisisRouting(boolean routed, List<Map<String, Object>> resources) {}

    /**
     * 조언 응답. 단건 조회/텍스트 조언 시 {@code guidance} 채워지고,
     * 감정 미지정 GET 은 {@code catalog} (전체 감정 선택지) 채워진다.
     */
    public record GuidanceResponse(
            JournalGuidanceCatalog.Guidance guidance,
            List<JournalGuidanceCatalog.Guidance> catalog,
            CrisisRouting crisis,
            String safetyFooter,
            String aiFooter) {}
}
