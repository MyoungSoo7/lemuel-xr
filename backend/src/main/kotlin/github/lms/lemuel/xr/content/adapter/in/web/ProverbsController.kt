package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.RecordProverbsInteractionUseCase
import github.lms.lemuel.xr.content.domain.ProverbsInteraction
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
 * /api/content/proverbs — 기준2 (잠언과 지혜) 주제별 조회.
 *
 * TRACK-A-1-4-WISDOM-EMOTION §3 (Theme 2). EcclesiastesController 스타일 (controller + catalog).
 * 잠언 지혜를 카테고리(주제)로 분류해 조회하고, 사용자가 고른 구절을
 * proverbs_interactions 테이블에 기록한다.
 *
 * 근거는 잠언(성경)만 — 주제·구절은 [ProverbsThemeCatalog] (실존 잠언)뿐.
 * 성경 외 자료 배제.
 *
 * 안전선 (R2/R3): 잠언의 단순화 위험(§3.4)을 피하기 위해 각 주제에 "결과 보장이 아니라 방향"
 * 안내를 동반. 정죄·회복 압박 배제. footer — "AI 보조 — 본문은 성경 참조".
 */
@RestController
@RequestMapping("/api/content/proverbs")
@Validated
class ProverbsController(
    private val recordInteraction: RecordProverbsInteractionUseCase,
) {

    /** 주제 목록 (프론트 주제 선택지). 잠언만 근거. */
    @GetMapping("/themes")
    fun themes(): ResponseEntity<ThemeListResponse> =
        ResponseEntity.ok(
            ThemeListResponse(ProverbsThemeCatalog.THEMES, SAFETY_FOOTER, AI_FOOTER),
        )

    /**
     * 주제(theme)로 잠언 구절 조회. 알 수 없는 theme 은 E_VALIDATION.
     */
    @GetMapping("/by-theme")
    fun byTheme(@RequestParam theme: String): ResponseEntity<ByThemeResponse> {
        val t = ProverbsThemeCatalog.byKey(theme)
            ?: throw AppException(ErrorCode.E_VALIDATION)
        return ResponseEntity.ok(ByThemeResponse(t, SAFETY_FOOTER, AI_FOOTER))
    }

    /**
     * 사용자가 고른 잠언 기록 — proverbs_interactions INSERT.
     * 기존 카드 추천 흐름과 동일 테이블 사용 (recommended_proverbs = 주제의 전체 구절).
     */
    @PostMapping("/interactions")
    fun record(@RequestBody req: InteractionRequest): ResponseEntity<InteractionResponse> {
        val userId = RequestContext.currentUserId()
        val e: ProverbsInteraction = recordInteraction.record(
            userId, req.theme, req.situation, req.chosenProverbRef, req.dimension,
        )

        return ResponseEntity.ok(
            InteractionResponse(
                e.id, ProverbsThemeCatalog.byKey(req.theme)!!.key,
                e.chosenProverbRef, SAFETY_FOOTER, AI_FOOTER,
            ),
        )
    }

    // --- DTOs ---

    data class InteractionRequest(
        @field:Size(max = 30) val theme: String?,
        @field:Size(max = 2000) val situation: String?,
        @field:Size(max = 20) val chosenProverbRef: String?,
        @field:Size(max = 20) val dimension: String?,
    )

    data class ThemeListResponse(
        val themes: List<ProverbsThemeCatalog.Theme>,
        val safetyFooter: String,
        val aiFooter: String,
    )

    data class ByThemeResponse(
        val theme: ProverbsThemeCatalog.Theme,
        val safetyFooter: String,
        val aiFooter: String,
    )

    data class InteractionResponse(
        val id: Long?,
        val theme: String,
        val chosenProverbRef: String?,
        val safetyFooter: String,
        val aiFooter: String,
    )

    companion object {
        private const val AI_FOOTER =
            "AI 보조 — 본문은 성경 참조. 잠언(성경) 외 자료는 사용하지 않습니다."

        private const val SAFETY_FOOTER =
            "잠언의 지혜는 결과를 보장하는 공식이 아니라 오늘 한 걸음의 방향입니다." +
                " 지금 지쳐 있다면, 모든 지혜를 다 지켜야 한다는 압박 없이 마음에 닿는 한 구절만 붙잡으셔도 괜찮습니다."
    }
}
