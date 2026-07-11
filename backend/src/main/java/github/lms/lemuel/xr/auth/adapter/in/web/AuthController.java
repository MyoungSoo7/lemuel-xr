package github.lms.lemuel.xr.auth.adapter.in.web;

import github.lms.lemuel.xr.auth.application.AcceptDisclaimerUseCase;
import github.lms.lemuel.xr.auth.application.GetCurrentUserUseCase;
import github.lms.lemuel.xr.auth.application.IssueGuestTokenUseCase;
import github.lms.lemuel.xr.auth.application.UpdateSafetyUseCase;
import github.lms.lemuel.xr.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/auth/guest — 디바이스별 게스트 발급
 * /api/users/me  — 현재 사용자 조회
 * /api/users/me/safety — 안전·취향 갱신
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final IssueGuestTokenUseCase issueGuest;
    private final GetCurrentUserUseCase getCurrent;
    private final UpdateSafetyUseCase updateSafety;
    private final AcceptDisclaimerUseCase acceptDisclaimer;

    @PostMapping("/api/auth/guest")
    public ResponseEntity<GuestResponse> guest(@RequestBody GuestRequest req) {
        var r = issueGuest.execute(req.deviceFingerprint(), req.deviceType());
        return ResponseEntity.ok(new GuestResponse(r.userId(), r.token(), r.expiresAt()));
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<UserResponse> me() {
        var u = getCurrent.execute(RequestContext.currentUserId());
        return ResponseEntity.ok(new UserResponse(
                u.getId(), u.getUserType(), u.getFaithTone(), u.getPreferredMode(),
                new SafetyDto(u.getHapticIntensity(), u.getSkipIntroSilence(), u.getDataRetentionDays()),
                new DisclaimerStatusDto(
                        u.getDisclaimerAcceptedAt() != null,
                        u.getDisclaimerVersion(),
                        AcceptDisclaimerUseCase.CURRENT_VERSION,
                        u.getDisclaimerAcceptedAt()
                ),
                u.getAiOptOut() != null ? u.getAiOptOut() : Boolean.FALSE
        ));
    }

    /**
     * "치료 도구가 아닙니다 + 1393 + AI 라벨링" 동의 endpoint.
     * 모든 콘텐츠 endpoint 는 DisclaimerGateFilter 가 이 동의 후에만 통과시킴.
     */
    @PostMapping("/api/auth/accept-disclaimer")
    public ResponseEntity<DisclaimerStatusDto> accept(HttpServletRequest req) {
        var r = acceptDisclaimer.execute(
                RequestContext.currentUserId(),
                req.getHeader("User-Agent"),
                req.getRemoteAddr()
        );
        return ResponseEntity.ok(new DisclaimerStatusDto(
                true, r.version(), AcceptDisclaimerUseCase.CURRENT_VERSION, r.acceptedAt()
        ));
    }

    /** AI opt-out 토글 (LLM 생성 콘텐츠 거절 — 큐레이션 정적 콘텐츠만 받음). */
    @PatchMapping("/api/users/me/ai-opt-out")
    public ResponseEntity<AiOptOutDto> patchAiOptOut(@RequestBody AiOptOutDto req) {
        var u = updateSafety.execute(RequestContext.currentUserId(),
                new UpdateSafetyUseCase.Patch(null, null, null, null, null,
                        req.optOut() != null && req.optOut()));
        return ResponseEntity.ok(new AiOptOutDto(u.getAiOptOut()));
    }

    @PatchMapping("/api/users/me/safety")
    public ResponseEntity<SafetyDto> updateSafety(@RequestBody SafetyDto patch) {
        var u = updateSafety.execute(RequestContext.currentUserId(),
                new UpdateSafetyUseCase.Patch(
                        patch.hapticIntensity(), patch.skipIntroSilence(),
                        null, null, patch.dataRetentionDays(), null));
        return ResponseEntity.ok(new SafetyDto(
                u.getHapticIntensity(), u.getSkipIntroSilence(), u.getDataRetentionDays()
        ));
    }

    public record GuestRequest(
            @Size(max = 255) String deviceFingerprint,
            @Size(max = 30) String deviceType
    ) {}

    public record GuestResponse(UUID userId, String token, Instant expiresAt) {}

    public record UserResponse(UUID userId, String userType, String faithTone,
                                String preferredMode, SafetyDto safety,
                                DisclaimerStatusDto disclaimer,
                                Boolean aiOptOut) {}

    public record SafetyDto(String hapticIntensity, Boolean skipIntroSilence, Integer dataRetentionDays) {}

    public record DisclaimerStatusDto(boolean accepted, String acceptedVersion,
                                        String currentVersion, OffsetDateTime acceptedAt) {}

    public record AiOptOutDto(Boolean optOut) {}
}
