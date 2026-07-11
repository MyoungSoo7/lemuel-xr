package github.lms.lemuel.xr.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.auth.adapter.out.persistence.DeviceJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.DeviceRepository;
import github.lms.lemuel.xr.auth.adapter.out.persistence.DisclaimerAcceptanceJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.DisclaimerAcceptanceRepository;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserRepository;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.common.jwt.JwtIssuer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * auth/application 4개 유스케이스 단위 테스트 — 게스트 발급/현재 사용자/디스클레이머 동의/안전 설정 갱신.
 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationTest {

    @Mock UserRepository users;
    @Mock DeviceRepository devices;
    @Mock JwtIssuer jwt;
    @Mock DisclaimerAcceptanceRepository acceptances;

    // ─────────────────────────── IssueGuestTokenUseCase ───────────────────────────

    @Test
    void issueGuest_새디바이스_사용자와디바이스_생성() {
        var uc = new IssueGuestTokenUseCase(users, devices, jwt);
        when(devices.findByDeviceFingerprint("fp-new")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(devices.save(any())).thenAnswer(i -> i.getArgument(0));
        Instant exp = Instant.now().plusSeconds(3600);
        when(jwt.issue(any(), any())).thenReturn(new JwtIssuer.IssuedToken("tok", exp));

        var r = uc.execute("fp-new", "quest3");

        assertThat(r.token()).isEqualTo("tok");
        assertThat(r.expiresAt()).isEqualTo(exp);
        assertThat(r.userId()).isNotNull();
        ArgumentCaptor<UserJpaEntity> userCap = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(users).save(userCap.capture());
        UserJpaEntity u = userCap.getValue();
        assertThat(u.getUserType()).isEqualTo("guest");
        assertThat(u.getFaithTone()).isEqualTo("balanced");
        assertThat(u.getHapticIntensity()).isEqualTo("medium");
        assertThat(u.getDataRetentionDays()).isEqualTo(90);
        assertThat(u.getSkipIntroSilence()).isFalse();
        verify(devices).save(any());
        verify(jwt).issue(r.userId(), "quest3");
    }

    @Test
    void issueGuest_기존디바이스_사용자재사용_lastSeen갱신() {
        var uc = new IssueGuestTokenUseCase(users, devices, jwt);
        UUID existingUser = UUID.randomUUID();
        DeviceJpaEntity dev = new DeviceJpaEntity();
        dev.setId(UUID.randomUUID());
        dev.setUserId(existingUser);
        when(devices.findByDeviceFingerprint("fp-old")).thenReturn(Optional.of(dev));
        when(devices.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwt.issue(any(), any())).thenReturn(new JwtIssuer.IssuedToken("tok2", Instant.now()));

        var r = uc.execute("fp-old", "visionpro");

        assertThat(r.userId()).isEqualTo(existingUser);
        assertThat(dev.getLastSeenAt()).isNotNull();
        verify(users, never()).save(any());  // 사용자 재사용 — 신규 INSERT 없음.
        verify(jwt).issue(existingUser, "visionpro");
    }

    @Test
    void issueGuest_fingerprint_null_익명사용자_생성() {
        var uc = new IssueGuestTokenUseCase(users, devices, jwt);
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(devices.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwt.issue(any(), any())).thenReturn(new JwtIssuer.IssuedToken("tok3", Instant.now()));

        var r = uc.execute(null, "web");

        assertThat(r.userId()).isNotNull();
        verify(devices, never()).findByDeviceFingerprint(any());
        verify(users).save(any());
    }

    // ─────────────────────────── GetCurrentUserUseCase ───────────────────────────

    @Test
    void getCurrentUser_존재하면_반환() {
        var uc = new GetCurrentUserUseCase(users);
        UUID uid = UUID.randomUUID();
        UserJpaEntity u = new UserJpaEntity();
        u.setId(uid);
        when(users.findById(uid)).thenReturn(Optional.of(u));

        assertThat(uc.execute(uid)).isSameAs(u);
    }

    @Test
    void getCurrentUser_없으면_E_AUTH_REQUIRED() {
        var uc = new GetCurrentUserUseCase(users);
        UUID uid = UUID.randomUUID();
        when(users.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.execute(uid))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_AUTH_REQUIRED);
    }

    // ─────────────────────────── AcceptDisclaimerUseCase ───────────────────────────

    @Test
    void acceptDisclaimer_사용자_갱신_및_audit_ip해시() {
        var uc = new AcceptDisclaimerUseCase(users, acceptances);
        UUID uid = UUID.randomUUID();
        UserJpaEntity u = new UserJpaEntity();
        u.setId(uid);
        when(users.findById(uid)).thenReturn(Optional.of(u));

        var r = uc.execute(uid, "Mozilla/5.0", "203.0.113.7");

        assertThat(r.version()).isEqualTo(AcceptDisclaimerUseCase.CURRENT_VERSION);
        assertThat(u.getDisclaimerAcceptedAt()).isNotNull();
        assertThat(u.getDisclaimerVersion()).isEqualTo(AcceptDisclaimerUseCase.CURRENT_VERSION);
        ArgumentCaptor<DisclaimerAcceptanceJpaEntity> cap =
                ArgumentCaptor.forClass(DisclaimerAcceptanceJpaEntity.class);
        verify(acceptances).save(cap.capture());
        DisclaimerAcceptanceJpaEntity a = cap.getValue();
        assertThat(a.getUserId()).isEqualTo(uid);
        assertThat(a.getUserAgent()).isEqualTo("Mozilla/5.0");
        // raw IP 저장 금지 — SHA-256 hex (64자) 만.
        assertThat(a.getIpHash()).hasSize(64).doesNotContain("203.0.113.7");
    }

    @Test
    void acceptDisclaimer_긴userAgent_255자_절단_null_ip는_null해시() {
        var uc = new AcceptDisclaimerUseCase(users, acceptances);
        UUID uid = UUID.randomUUID();
        UserJpaEntity u = new UserJpaEntity();
        u.setId(uid);
        when(users.findById(uid)).thenReturn(Optional.of(u));
        String longUa = "x".repeat(400);

        uc.execute(uid, longUa, null);

        ArgumentCaptor<DisclaimerAcceptanceJpaEntity> cap =
                ArgumentCaptor.forClass(DisclaimerAcceptanceJpaEntity.class);
        verify(acceptances).save(cap.capture());
        assertThat(cap.getValue().getUserAgent()).hasSize(255);
        assertThat(cap.getValue().getIpHash()).isNull();
    }

    @Test
    void acceptDisclaimer_사용자없으면_E_AUTH_REQUIRED() {
        var uc = new AcceptDisclaimerUseCase(users, acceptances);
        UUID uid = UUID.randomUUID();
        when(users.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.execute(uid, "ua", "1.2.3.4"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_AUTH_REQUIRED);
        verify(acceptances, never()).save(any());
    }

    // ─────────────────────────── UpdateSafetyUseCase ───────────────────────────

    @Test
    void updateSafety_모든필드_적용() {
        var uc = new UpdateSafetyUseCase(users);
        UUID uid = UUID.randomUUID();
        UserJpaEntity u = new UserJpaEntity();
        u.setId(uid);
        OffsetDateTime before = OffsetDateTime.now().minusHours(1);
        u.setUpdatedAt(before);
        when(users.findById(uid)).thenReturn(Optional.of(u));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        var patch = new UpdateSafetyUseCase.Patch("high", true, "strong", "spiritual", 30, true);
        UserJpaEntity result = uc.execute(uid, patch);

        assertThat(result.getHapticIntensity()).isEqualTo("high");
        assertThat(result.getSkipIntroSilence()).isTrue();
        assertThat(result.getFaithTone()).isEqualTo("strong");
        assertThat(result.getPreferredMode()).isEqualTo("spiritual");
        assertThat(result.getDataRetentionDays()).isEqualTo(30);
        assertThat(result.getAiOptOut()).isTrue();
        assertThat(result.getUpdatedAt()).isAfter(before);
    }

    @Test
    void updateSafety_모두null_updatedAt만_갱신_기존필드유지() {
        var uc = new UpdateSafetyUseCase(users);
        UUID uid = UUID.randomUUID();
        UserJpaEntity u = new UserJpaEntity();
        u.setId(uid);
        u.setHapticIntensity("low");
        u.setFaithTone("soft");
        when(users.findById(uid)).thenReturn(Optional.of(u));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        var patch = new UpdateSafetyUseCase.Patch(null, null, null, null, null, null);
        UserJpaEntity result = uc.execute(uid, patch);

        // null 패치는 기존 값 보존.
        assertThat(result.getHapticIntensity()).isEqualTo("low");
        assertThat(result.getFaithTone()).isEqualTo("soft");
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateSafety_사용자없으면_E_AUTH_REQUIRED() {
        var uc = new UpdateSafetyUseCase(users);
        UUID uid = UUID.randomUUID();
        when(users.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.execute(uid,
                new UpdateSafetyUseCase.Patch(null, null, null, null, null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_AUTH_REQUIRED);
    }
}
