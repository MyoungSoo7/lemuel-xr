package github.lms.lemuel.xr.auth.adapter.in.web;

import github.lms.lemuel.xr.auth.application.GetCurrentUserUseCase;
import github.lms.lemuel.xr.auth.application.IssueGuestTokenUseCase;
import github.lms.lemuel.xr.auth.application.UpdateSafetyUseCase;
import github.lms.lemuel.xr.common.web.RequestContext;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
                new SafetyDto(u.getHapticIntensity(), u.getSkipIntroSilence(), u.getDataRetentionDays())
        ));
    }

    @PatchMapping("/api/users/me/safety")
    public ResponseEntity<SafetyDto> updateSafety(@RequestBody SafetyDto patch) {
        var u = updateSafety.execute(RequestContext.currentUserId(),
                new UpdateSafetyUseCase.Patch(
                        patch.hapticIntensity(), patch.skipIntroSilence(),
                        null, null, patch.dataRetentionDays()));
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
                                String preferredMode, SafetyDto safety) {}

    public record SafetyDto(String hapticIntensity, Boolean skipIntroSilence, Integer dataRetentionDays) {}
}
