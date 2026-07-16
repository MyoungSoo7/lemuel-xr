package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.EcclesiastesViewUseCase
import github.lms.lemuel.xr.content.domain.EcclesiastesView
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * /api/content/ecclesiastes — 기준4 (전도서와 인생).
 *
 * TRACK-A-1-4-WISDOM-EMOTION §4 (Theme 3). PracticeReflectionController 스타일 (controller + repo).
 * 인생에 관한 전도서의 진리를 카테고리로 분류해 보여주고, 사용자가 자신의 '인생 계절'(user_season)
 * 에 맞춰 헛됨(futility)과 의미(meaning)를 성찰·기록한다. ecclesiastes_views 테이블 사용.
 *
 * 근거는 성경만 — 카테고리·계절·성구는 전도서 + 관련 성구뿐. 성경 외 자료 배제.
 *
 * 안전선 (R1~R5):
 * - R1 — futility_note / meaning_note 를 CrisisKeywordScanner 로 스캔. 자해·자살 키워드 매칭 시
 *   RecordSafetyAlertUseCase 로 safety_alert INSERT + 위기 자원 반환. 응답 crisis.routed=true
 *   → 프론트가 일기(#1)/위기 카드로 라우팅. 전도서는 nihilism 을 강화할 수 있어(§4.4) 스캔이 특히 중요.
 *   법적 의무 — 완화·제거 금지.
 * - R2 — 전도서의 '헛됨'을 절망이 아니라 "해 아래 유한함의 정직한 인정 → 창조주 경외(전 12:13)"로
 *   마무리하는 톤. safetyFooter + conclusionInvite 로 항상 결론까지 안내. R3 압박 배제.
 * - footer — "AI 보조 — 본문은 성경 참조" (성경 외 근거 없음).
 */
@RestController
@RequestMapping("/api/content/ecclesiastes")
@Validated
class EcclesiastesController(
    private val ecclesiastesView: EcclesiastesViewUseCase,
) {

    /**
     * 인생에 관한 전도서 진리 — 카테고리 분류 조회.
     *
     * 사용자의 '인생 계절'을 고를 수 있게 카테고리별 전도서 본문(chapter_ref)과 성구를 함께 제공.
     * 성경만 근거 — 정적 상수(EcclesiastesCatalog). DB 시드 불필요.
     */
    @GetMapping("/categories")
    fun categories(): ResponseEntity<CategoryListResponse> =
        ResponseEntity.ok(
            CategoryListResponse(
                EcclesiastesCatalog.CATEGORIES, EcclesiastesCatalog.SEASONS, AI_FOOTER,
            ),
        )

    /**
     * 사용자 view 기록. futility/meaning 자유 기록은 R1 키워드 스캔 통과.
     */
    @PostMapping
    fun create(@RequestBody req: CreateRequest): ResponseEntity<EcclesiastesResponse> {
        val userId = RequestContext.currentUserId()

        val result = ecclesiastesView.create(
            userId, req.chapterRef, req.userSeason,
            req.futilityNote, req.meaningNote,
            req.listenedAudio, req.conclusionViewed,
        )

        return ResponseEntity.ok(
            EcclesiastesResponse(
                toDto(result.view),
                CrisisRouting(result.alert.triggered, result.alert.shownResources),
                SAFETY_FOOTER,
                CONCLUSION_INVITE,
                AI_FOOTER,
            ),
        )
    }

    /** 본인 전도서 성찰 이력 + 결론(전 12:13)까지 본 누적 수(§4.4 신호). */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<EcclesiastesListResponse> {
        val userId = RequestContext.currentUserId()
        val items = ecclesiastesView.list(userId, limit).map { toDto(it) }
        val conclusionCount = ecclesiastesView.conclusionViewedCount(userId)
        return ResponseEntity.ok(EcclesiastesListResponse(items, conclusionCount, CONCLUSION_INVITE))
    }

    private fun toDto(e: EcclesiastesView): EcclesiastesDto =
        EcclesiastesDto(
            e.id, e.chapterRef, e.userSeason,
            e.futilityNote, e.meaningNote, e.listenedAudio,
            e.conclusionViewed, e.createdAt,
        )

    // --- DTOs ---

    data class CreateRequest(
        @field:Size(max = 20) val chapterRef: String?,
        @field:Size(max = 20) val userSeason: String?,
        @field:Size(max = 5000) val futilityNote: String?,
        @field:Size(max = 5000) val meaningNote: String?,
        val listenedAudio: Boolean?,
        val conclusionViewed: Boolean?,
    )

    data class EcclesiastesDto(
        val id: Long?,
        val chapterRef: String?,
        val userSeason: String?,
        val futilityNote: String?,
        val meaningNote: String?,
        val listenedAudio: Boolean?,
        val conclusionViewed: Boolean?,
        val createdAt: LocalDateTime?,
    )

    /** R1 — 위기 키워드 매칭 시 프론트가 일기(#1)/위기 카드로 라우팅. */
    data class CrisisRouting(val routed: Boolean, val resources: List<Map<String, Any?>>)

    data class EcclesiastesResponse(
        val view: EcclesiastesDto,
        val crisis: CrisisRouting,
        val safetyFooter: String,
        val conclusionInvite: String,
        val aiFooter: String,
    )

    data class EcclesiastesListResponse(
        val items: List<EcclesiastesDto>,
        val conclusionViewedCount: Long,
        val conclusionInvite: String,
    )

    data class CategoryListResponse(
        val categories: List<EcclesiastesCatalog.Category>,
        val seasons: List<EcclesiastesCatalog.Season>,
        val aiFooter: String,
    )

    companion object {
        private const val AI_FOOTER =
            "AI 보조 — 본문은 성경 참조. 전도서와 관련 성구 외 자료는 사용하지 않습니다."

        /**
         * §4.4 결론 회피 금지 — '헛됨'만 노출하지 않고 창조주 경외(전 12:13)로 마무리하는 안내 문구.
         * 절망이 아니라 "해 아래 유한함의 정직한 인정" 톤 (R2). 고난 미화·방치로 미끄러지지 않게.
         */
        private const val SAFETY_FOOTER =
            "전도서의 \"헛됨\"은 인생을 포기하라는 말이 아니라, 해 아래 유한함을 정직하게 인정하는 자리입니다." +
                " 솔로몬은 그 인정을 절망이 아니라 창조주를 경외하는 결론으로 마무리합니다" +
                " — \"일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다\" (전 12:13)." +
                " 무엇을 더 해내야 한다는 압박 없이, 오늘의 유한함을 그대로 하나님 앞에 두어도 괜찮습니다."

        /** §4.4 — 응답에 항상 동반해 전 12:13 결론까지 함께 보도록 초대. */
        private const val CONCLUSION_INVITE =
            "1~11장의 헛됨만 보고 멈추지 마시고, 전 12:13~14 결론까지 함께 보실 수 있습니다."
    }
}
