package github.lms.lemuel.xr.values.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.values.application.GetValueProfileUseCase
import github.lms.lemuel.xr.values.application.RecordPracticeUseCase
import github.lms.lemuel.xr.values.application.UpdateValueProfileUseCase
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

/**
 * /api/values/ — 자기만의 7 가치 빌더 (CROSS-MAPPING-VR-AR.md §5).
 *
 * 4 endpoint:
 * - GET  /me        — 본인 프로파일 + 최근 7일 통계 + CDR Index
 * - POST /profile   — 7 가치 정의 (부분 갱신)
 * - POST /practice  — 오늘의 실천 1건 기록
 * - GET  /streak    — TODO Phase 2 — 연속 실천일
 */
@RestController
@RequestMapping("/api/values")
@Validated
class ValuesController(
    private val getProfileUc: GetValueProfileUseCase,
    private val updateProfileUc: UpdateValueProfileUseCase,
    private val recordPracticeUc: RecordPracticeUseCase,
) {

    @GetMapping("/me")
    fun me(): ResponseEntity<ProfileResponse> {
        val r = getProfileUc.execute(RequestContext.currentUserId())
        return ResponseEntity.ok(
            ProfileResponse(
                r.valuesJson,
                StatsDto(r.totalPractices7d, r.countByValue, r.cdrIndex, r.tier),
            ),
        )
    }

    @PostMapping("/profile")
    fun upsert(@RequestBody @NotNull patch: Map<String, Any?>): ResponseEntity<ProfileResponse> {
        val p = updateProfileUc.execute(RequestContext.currentUserId(), patch)
        val stats = getProfileUc.execute(RequestContext.currentUserId())
        return ResponseEntity.ok(
            ProfileResponse(
                p.valuesJson,
                StatsDto(stats.totalPractices7d, stats.countByValue, stats.cdrIndex, stats.tier),
            ),
        )
    }

    @PostMapping("/practice")
    fun practice(@RequestBody req: PracticeRequest): ResponseEntity<PracticeDto> {
        val p = recordPracticeUc.execute(
            RequestContext.currentUserId(),
            req.valueId!!,
            req.durationSec,
            req.note,
            req.linkedCharacter,
            req.linkedGameSession,
        )
        return ResponseEntity.ok(
            PracticeDto(
                p.id, p.valueId, p.practicedAt,
                p.durationSec, p.note,
                p.linkedCharacter, p.linkedGameSession,
            ),
        )
    }

    // --- DTOs ---
    data class ProfileResponse(val values: Map<String, Any?>, val stats: StatsDto)

    data class StatsDto(
        val totalPractices7d: Int,
        val countByValue: Map<String, Int>,
        val cdrIndex: Int,
        val tier: String,
    )

    data class PracticeRequest(
        @field:NotNull @field:Min(1) @field:Max(7) val valueId: Int?,
        val durationSec: Int?,
        val note: String?,
        val linkedCharacter: String?,
        val linkedGameSession: UUID?,
    )

    data class PracticeDto(
        val id: Long?,
        val valueId: Short,
        val practicedAt: OffsetDateTime,
        val durationSec: Int?,
        val note: String?,
        val linkedCharacter: String?,
        val linkedGameSession: UUID?,
    )
}
