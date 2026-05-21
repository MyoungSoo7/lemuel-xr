package github.lms.lemuel.xr.auth.application;

import github.lms.lemuel.xr.auth.adapter.out.persistence.UserJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserRepository;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 안전·취향 설정 갱신 (haptic·skipSilence·faithTone·preferredMode 등). */
@Service
@RequiredArgsConstructor
public class UpdateSafetyUseCase {

    private final UserRepository users;

    @Transactional
    public UserJpaEntity execute(UUID userId, Patch patch) {
        UserJpaEntity u = users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.E_AUTH_REQUIRED));
        if (patch.hapticIntensity() != null) u.setHapticIntensity(patch.hapticIntensity());
        if (patch.skipIntroSilence() != null) u.setSkipIntroSilence(patch.skipIntroSilence());
        if (patch.faithTone() != null) u.setFaithTone(patch.faithTone());
        if (patch.preferredMode() != null) u.setPreferredMode(patch.preferredMode());
        if (patch.dataRetentionDays() != null) u.setDataRetentionDays(patch.dataRetentionDays());
        if (patch.aiOptOut() != null) u.setAiOptOut(patch.aiOptOut());
        u.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return users.save(u);
    }

    public record Patch(
            String hapticIntensity,
            Boolean skipIntroSilence,
            String faithTone,
            String preferredMode,
            Integer dataRetentionDays,
            Boolean aiOptOut
    ) {}
}
