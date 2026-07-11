package github.lms.lemuel.xr.auth.application;

import github.lms.lemuel.xr.auth.adapter.out.persistence.DeviceJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.DeviceRepository;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserRepository;
import github.lms.lemuel.xr.common.jwt.JwtIssuer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게스트 사용자 발급 + 디바이스 등록 + JWT.
 *
 * <p>같은 device_fingerprint 가 다시 와도 새 사용자 만들지 않고 *기존 사용자* 의
 * JWT 만 재발급. 디바이스 재진입 연속성 보장 (BACKEND-API-DESIGN.md §3.1).</p>
 */
@Service
@RequiredArgsConstructor
public class IssueGuestTokenUseCase {

    private final UserRepository users;
    private final DeviceRepository devices;
    private final JwtIssuer jwt;

    @Transactional
    public Result execute(String deviceFingerprint, String deviceType) {
        UUID userId;
        if (deviceFingerprint != null) {
            // 기존 디바이스 → 사용자 재사용
            var existing = devices.findByDeviceFingerprint(deviceFingerprint);
            if (existing.isPresent()) {
                userId = existing.get().getUserId();
                existing.get().setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
                devices.save(existing.get());
            } else {
                userId = createUserAndDevice(deviceFingerprint, deviceType);
            }
        } else {
            userId = createUserAndDevice(null, deviceType);
        }
        var issued = jwt.issue(userId, deviceType);
        return new Result(userId, issued.token(), issued.expiresAt());
    }

    private UUID createUserAndDevice(String fingerprint, String deviceType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDateTime nowLocal = LocalDateTime.now(ZoneOffset.UTC);
        UserJpaEntity user = new UserJpaEntity();
        user.setId(UUID.randomUUID());
        user.setCreatedAt(nowLocal);    // users.created_at = TIMESTAMP (V1)
        user.setUpdatedAt(now);          // users.updated_at = TIMESTAMPTZ (V3)
        user.setUserType("guest");
        user.setFaithTone("balanced");
        user.setHapticIntensity("medium");
        user.setSkipIntroSilence(Boolean.FALSE);
        user.setDataRetentionDays(90);
        users.save(user);

        DeviceJpaEntity device = new DeviceJpaEntity();
        device.setId(UUID.randomUUID());
        device.setUserId(user.getId());
        device.setDeviceFingerprint(fingerprint);
        device.setDeviceType(deviceType);
        device.setCreatedAt(now);
        device.setLastSeenAt(now);
        devices.save(device);

        return user.getId();
    }

    public record Result(UUID userId, String token, Instant expiresAt) {}
}
