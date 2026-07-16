package github.lms.lemuel.xr.auth.adapter.`in`.web

import github.lms.lemuel.xr.auth.application.AcceptDisclaimerUseCase
import github.lms.lemuel.xr.auth.application.GetCurrentUserUseCase
import github.lms.lemuel.xr.auth.application.IssueGuestTokenUseCase
import github.lms.lemuel.xr.auth.application.UpdateSafetyUseCase
import github.lms.lemuel.xr.common.web.RequestContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * /api/auth/guest — 디바이스별 게스트 발급
 * /api/users/me  — 현재 사용자 조회
 * /api/users/me/safety — 안전·취향 갱신
 */
@RestController
class AuthController(
    private val issueGuest: IssueGuestTokenUseCase,
    private val getCurrent: GetCurrentUserUseCase,
    private val updateSafety: UpdateSafetyUseCase,
    private val acceptDisclaimer: AcceptDisclaimerUseCase,
) {

    @PostMapping("/api/auth/guest")
    fun guest(@RequestBody req: GuestRequest): ResponseEntity<GuestResponse> {
        val r = issueGuest.execute(req.deviceFingerprint, req.deviceType)
        return ResponseEntity.ok(GuestResponse(r.userId, r.token, r.expiresAt))
    }

    @GetMapping("/api/users/me")
    fun me(): ResponseEntity<UserResponse> {
        val u = getCurrent.execute(RequestContext.currentUserId())
        return ResponseEntity.ok(
            UserResponse(
                u.id, u.userType, u.faithTone, u.preferredMode,
                SafetyDto(u.hapticIntensity, u.skipIntroSilence, u.dataRetentionDays),
                DisclaimerStatusDto(
                    u.disclaimerAcceptedAt != null,
                    u.disclaimerVersion,
                    AcceptDisclaimerUseCase.CURRENT_VERSION,
                    u.disclaimerAcceptedAt,
                ),
                u.aiOptOut ?: false,
            ),
        )
    }

    /**
     * "치료 도구가 아닙니다 + 1393 + AI 라벨링" 동의 endpoint.
     * 모든 콘텐츠 endpoint 는 DisclaimerGateFilter 가 이 동의 후에만 통과시킴.
     */
    @PostMapping("/api/auth/accept-disclaimer")
    fun accept(req: HttpServletRequest): ResponseEntity<DisclaimerStatusDto> {
        val r = acceptDisclaimer.execute(
            RequestContext.currentUserId(),
            req.getHeader("User-Agent"),
            req.remoteAddr,
        )
        return ResponseEntity.ok(
            DisclaimerStatusDto(
                true, r.version, AcceptDisclaimerUseCase.CURRENT_VERSION, r.acceptedAt,
            ),
        )
    }

    /** AI opt-out 토글 (LLM 생성 콘텐츠 거절 — 큐레이션 정적 콘텐츠만 받음). */
    @PatchMapping("/api/users/me/ai-opt-out")
    fun patchAiOptOut(@RequestBody req: AiOptOutDto): ResponseEntity<AiOptOutDto> {
        val u = updateSafety.execute(
            RequestContext.currentUserId(),
            UpdateSafetyUseCase.Patch(null, null, null, null, null, req.optOut == true),
        )
        return ResponseEntity.ok(AiOptOutDto(u.aiOptOut))
    }

    @PatchMapping("/api/users/me/safety")
    fun updateSafety(@RequestBody patch: SafetyDto): ResponseEntity<SafetyDto> {
        val u = updateSafety.execute(
            RequestContext.currentUserId(),
            UpdateSafetyUseCase.Patch(
                patch.hapticIntensity, patch.skipIntroSilence,
                null, null, patch.dataRetentionDays, null,
            ),
        )
        return ResponseEntity.ok(
            SafetyDto(u.hapticIntensity, u.skipIntroSilence, u.dataRetentionDays),
        )
    }

    data class GuestRequest(
        @field:Size(max = 255) val deviceFingerprint: String?,
        @field:Size(max = 30) val deviceType: String?,
    )

    data class GuestResponse(val userId: UUID, val token: String, val expiresAt: Instant)

    data class UserResponse(
        val userId: UUID?,
        val userType: String?,
        val faithTone: String?,
        val preferredMode: String?,
        val safety: SafetyDto,
        val disclaimer: DisclaimerStatusDto,
        val aiOptOut: Boolean,
    )

    data class SafetyDto(
        val hapticIntensity: String?,
        val skipIntroSilence: Boolean?,
        val dataRetentionDays: Int?,
    )

    data class DisclaimerStatusDto(
        val accepted: Boolean,
        val acceptedVersion: String?,
        val currentVersion: String,
        val acceptedAt: OffsetDateTime?,
    )

    data class AiOptOutDto(val optOut: Boolean?)
}
