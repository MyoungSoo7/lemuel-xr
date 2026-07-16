package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.JournalGuidanceUseCase
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * /api/content/journal/guidance — 기준1 (일기와 묵상) 성경 기반 규칙형 조언.
 *
 * TRACK-A-1-4-WISDOM-EMOTION §2 (Theme 1). JournalController + EcclesiastesController 스타일.
 * 일기에 대해 "상담/조언" 을 돌려주되 LLM 을 붙이지 않고(그건 별도 게이트),
 * 감정에 맞는 성경 구절 + 성찰 질문 + (위기 시) 상담 자원 을 정적 카탈로그
 * [JournalGuidanceCatalog] 로 반환한다.
 *
 * 근거는 성경만 — 구절·질문은 시편/잠언/복음서 등 성경 본문뿐. 성경 외 자료 배제.
 *
 * 안전선 (R1~R5):
 * - R1 — POST 의 일기 텍스트를 CrisisKeywordScanner 로 스캔. 자해·자살 키워드 매칭 시
 *   RecordSafetyAlertUseCase 로 safety_alert INSERT + 위기 자원 반환.
 *   응답 crisis.routed=true → 프론트가 일기(#1)/위기 카드로 라우팅.
 *   법적 의무 — 완화·제거 금지.
 * - R2/R3 — 조언은 "그 감정도 성경 안에 있다"는 인증(validation). 정죄·회복 압박 배제.
 *   성찰 질문은 답을 강요하지 않고 여는 형태.
 * - footer — aiFooter "AI 보조 — 본문은 성경 참조" (성경 외 근거 없음).
 *
 * 감정 감지: LLM 을 새로 붙이지 않으므로, POST 는 명시 emotion 우선 →
 * 없으면 룰 기반 키워드 감지 → 실패 시 CONFUSED fallback (Emotion.fromString 규칙과 동일).
 */
@RestController
@RequestMapping("/api/content/journal/guidance")
@Validated
class JournalGuidanceController(
    private val journalGuidance: JournalGuidanceUseCase,
) {

    /**
     * GET — 감정으로 성경 기반 조언 조회. 감정 미지정이면 전체 카탈로그(감정 선택지) 반환.
     * 카탈로그 조회이므로 위기 스캔 없음(입력 텍스트가 없음).
     */
    @GetMapping
    fun byEmotion(
        @RequestParam(required = false) emotion: String?,
    ): ResponseEntity<GuidanceResponse> {
        if (emotion == null || emotion.isBlank()) {
            return ResponseEntity.ok(
                GuidanceResponse(
                    null, JournalGuidanceCatalog.all(),
                    CrisisRouting(false, emptyList()),
                    SAFETY_FOOTER, AI_FOOTER,
                ),
            )
        }
        val g = journalGuidance.forEmotion(emotion)
        return ResponseEntity.ok(
            GuidanceResponse(
                g, emptyList(),
                CrisisRouting(false, emptyList()),
                SAFETY_FOOTER, AI_FOOTER,
            ),
        )
    }

    /**
     * POST — 일기 텍스트 → 조언. R1: 텍스트를 위기 키워드 스캔 후, 감정 감지 →
     * 성경 구절 + 성찰 질문 반환. 위기 시 상담 자원 동반 + 일기(#1) 라우팅 신호.
     */
    @PostMapping
    fun fromText(@RequestBody req: GuidanceRequest): ResponseEntity<GuidanceResponse> {
        val userId = RequestContext.currentUserId()

        val result = journalGuidance.fromText(userId, req.text, req.emotion)

        return ResponseEntity.ok(
            GuidanceResponse(
                result.guidance, emptyList(),
                CrisisRouting(result.crisisRouted, result.resources),
                SAFETY_FOOTER, AI_FOOTER,
            ),
        )
    }

    // --- DTOs ---

    data class GuidanceRequest(
        @field:Size(max = 5000) val text: String?,
        @field:Size(max = 30) val emotion: String?,
    )

    /** R1 — 위기 키워드 매칭 시 프론트가 일기(#1)/위기 카드로 라우팅. */
    data class CrisisRouting(val routed: Boolean, val resources: List<Map<String, Any?>>)

    /**
     * 조언 응답. 단건 조회/텍스트 조언 시 guidance 채워지고,
     * 감정 미지정 GET 은 catalog (전체 감정 선택지) 채워진다.
     */
    data class GuidanceResponse(
        val guidance: JournalGuidanceCatalog.Guidance?,
        val catalog: List<JournalGuidanceCatalog.Guidance>,
        val crisis: CrisisRouting,
        val safetyFooter: String,
        val aiFooter: String,
    )

    companion object {
        private const val AI_FOOTER =
            "AI 보조 — 본문은 성경 참조. 성경(시편·잠언·복음서 등) 외 자료는 조언 근거로 쓰지 않습니다."

        private const val SAFETY_FOOTER =
            "이 조언은 성경 구절과 성찰 질문일 뿐, 의료·심리 진단이나 치료를 대체하지 않습니다." +
                " 답을 강요하지 않습니다 — 오늘 마음에 닿는 한 구절, 한 질문만 붙잡으셔도 충분합니다."
    }
}
