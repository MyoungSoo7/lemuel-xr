package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.PracticeReflectionUseCase
import github.lms.lemuel.xr.content.application.PracticeSafetyFooterCatalog
import github.lms.lemuel.xr.content.domain.PracticeReflection
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
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
 * /api/content/practice — Theme 6 (마음 지킴, 잠 4:23) · 7 (사람 두려움, 잠 29:25/사 51:7)
 * 실천/성찰 기록.
 *
 * TRACK-A-5-7-ACTION-GUIDANCE §3·§4·§7. JournalController 스타일 (controller + repo).
 *
 * 안전선 (R1~R5):
 * - R1 — 사용자 자유 기록(situation)을 CrisisKeywordScanner 로 스캔.
 *   자해·자살 키워드 매칭 시 RecordSafetyAlertUseCase 로 safety_alert INSERT +
 *   위기 자원(109 등) 반환. 응답 crisis.routed=true → 프론트가 일기(#1)/위기 카드로 라우팅.
 *   법적 의무 — 완화·제거 금지.
 * - R2 — 응답 safetyFooter 로 피해 상황 보호 문구 항상 동반.
 * - footer — "AI 보조 — 본문은 성경 참조" (성경 외 근거 없음).
 */
@RestController
@RequestMapping("/api/content/practice")
@Validated
class PracticeReflectionController(
    private val practiceReflection: PracticeReflectionUseCase,
    private val safetyFooters: PracticeSafetyFooterCatalog,
) {

    /**
     * Theme 6/7 실천 기록 저장. situation 은 R1 키워드 스캔 통과.
     */
    @PostMapping
    fun create(@RequestBody req: CreateRequest): ResponseEntity<PracticeResponse> {
        val userId = RequestContext.currentUserId()

        val result = practiceReflection.create(
            userId, req.topicId, req.practiceKind, req.situation,
            req.reflection, req.actionTaken, req.scriptureRef, req.dimension,
        )

        return ResponseEntity.ok(
            PracticeResponse(
                toDto(result.reflection),
                CrisisRouting(result.alert.triggered, result.alert.shownResources),
                safetyFooters.forTopic(req.topicId!!),
                AI_FOOTER,
            ),
        )
    }

    /** 주제별 실천 이력 + 누적 행동 카운트 (§7 "행동 자유도" 신호). */
    @GetMapping
    fun list(
        @RequestParam @Min(6) @Max(7) topicId: Short,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<PracticeListResponse> {
        val userId = RequestContext.currentUserId()
        val items = practiceReflection.list(userId, topicId, limit).map { toDto(it) }
        val actionCount = practiceReflection.actionCount(userId, topicId)
        return ResponseEntity.ok(PracticeListResponse(topicId, items, actionCount))
    }

    private fun toDto(e: PracticeReflection): PracticeDto =
        PracticeDto(
            e.id, e.topicId, e.practiceKind,
            e.situation, e.reflection, e.actionTaken,
            e.scriptureRef, e.dimension, e.createdAt,
        )

    // --- DTOs ---

    data class CreateRequest(
        @field:NotNull @field:Min(6) @field:Max(7) val topicId: Short?,
        @field:NotNull @field:Size(max = 30) val practiceKind: String?,
        @field:Size(max = 5000) val situation: String?,
        val reflection: Map<String, Any?>?,
        val actionTaken: Boolean?,
        @field:Size(max = 50) val scriptureRef: String?,
        @field:Size(max = 20) val dimension: String?,
    )

    data class PracticeDto(
        val id: Long?,
        val topicId: Short?,
        val practiceKind: String?,
        val situation: String?,
        val reflection: Map<String, Any?>?,
        val actionTaken: Boolean?,
        val scriptureRef: String?,
        val dimension: String?,
        val createdAt: LocalDateTime?,
    )

    /** R1 — 위기 키워드 매칭 시 프론트가 일기(#1)/위기 카드로 라우팅. */
    data class CrisisRouting(val routed: Boolean, val resources: List<Map<String, Any?>>)

    data class PracticeResponse(
        val practice: PracticeDto,
        val crisis: CrisisRouting,
        val safetyFooter: String,
        val aiFooter: String,
    )

    data class PracticeListResponse(
        val topicId: Short,
        val items: List<PracticeDto>,
        val actionCount: Long,
    )

    companion object {
        /** "AI 보조 — 본문은 성경 참조" (§10 출시 전 체크리스트). */
        private const val AI_FOOTER = "AI 보조 — storyteller, 본문은 성경 참조"
    }
}
