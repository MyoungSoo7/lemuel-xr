package github.lms.lemuel.xr.auth.application;

import github.lms.lemuel.xr.auth.adapter.out.persistence.DisclaimerAcceptanceJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.DisclaimerAcceptanceRepository;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserRepository;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "치료 도구 아님 + 위기 자원 + AI 라벨링" 디스클레이머 동의 처리.
 *
 * <p>users.disclaimer_accepted_at 갱신 + disclaimer_acceptances audit row 추가.
 * raw IP 는 저장하지 않고 SHA-256 hash 만 — ETHICS-LEGAL §2.2 / 개인정보보호법 준수.</p>
 */
@Service
@RequiredArgsConstructor
public class AcceptDisclaimerUseCase {

    /** 현재 disclaimer 본문 버전 — 본문 변경 시 bump → 모든 사용자 재동의. */
    public static final String CURRENT_VERSION = "1.0";

    private final UserRepository users;
    private final DisclaimerAcceptanceRepository acceptances;

    @Transactional
    public Result execute(UUID userId, String userAgent, String remoteAddr) {
        UserJpaEntity u = users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.E_AUTH_REQUIRED));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        u.setDisclaimerAcceptedAt(now);
        u.setDisclaimerVersion(CURRENT_VERSION);
        u.setUpdatedAt(now);

        DisclaimerAcceptanceJpaEntity a = new DisclaimerAcceptanceJpaEntity();
        a.setUserId(userId);
        a.setAcceptedAt(now);
        a.setDisclaimerVersion(CURRENT_VERSION);
        a.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        a.setIpHash(hashIp(remoteAddr));
        acceptances.save(a);

        return new Result(userId, now, CURRENT_VERSION);
    }

    private String hashIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public record Result(UUID userId, OffsetDateTime acceptedAt, String version) {}
}
