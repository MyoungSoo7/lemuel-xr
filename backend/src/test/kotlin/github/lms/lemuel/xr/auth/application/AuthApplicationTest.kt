package github.lms.lemuel.xr.auth.application

import github.lms.lemuel.xr.auth.application.port.out.DevicePort
import github.lms.lemuel.xr.auth.application.port.out.DisclaimerAcceptancePort
import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.Device
import github.lms.lemuel.xr.auth.domain.DisclaimerAcceptance
import github.lms.lemuel.xr.auth.domain.User
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.common.jwt.JwtIssuer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

/**
 * auth/application 4개 유스케이스 단위 테스트 — 게스트 발급/현재 사용자/디스클레이머 동의/안전 설정 갱신.
 *
 * Round 2 헥사고날 리팩터 후: 포트는 순수 도메인 record([User]/[Device]/
 * [DisclaimerAcceptance])만 주고받는다 (JpaEntity 노출 없음).
 */
class AuthApplicationTest {

    private val users: UserPort = mock()
    private val devices: DevicePort = mock()
    private val jwt: JwtIssuer = mock()
    private val acceptances: DisclaimerAcceptancePort = mock()

    /** 테스트용 최소 User 도메인 — 지정한 id 외 필드는 null/기본. */
    private fun userWithId(id: UUID): User =
        User(
            id, LocalDateTime.now(), OffsetDateTime.now(),
            "guest", null, null, null, null, null, null, null, null, null, false,
        )

    // ─────────────────────────── IssueGuestTokenUseCase ───────────────────────────

    @Test
    fun `issueGuest 새디바이스 사용자와디바이스 생성`() {
        val uc = IssueGuestTokenUseCase(users, devices, jwt)
        whenever(devices.findByDeviceFingerprint("fp-new")).thenReturn(Optional.empty())
        whenever(users.save(any())).thenAnswer { it.getArgument(0) }
        whenever(devices.save(any())).thenAnswer { it.getArgument(0) }
        val exp = Instant.now().plusSeconds(3600)
        whenever(jwt.issue(any(), any())).thenReturn(JwtIssuer.IssuedToken("tok", exp))

        val r = uc.execute("fp-new", "quest3")

        assertThat(r.token).isEqualTo("tok")
        assertThat(r.expiresAt).isEqualTo(exp)
        assertThat(r.userId).isNotNull()
        val userCap = argumentCaptor<User>()
        verify(users).save(userCap.capture())
        val u = userCap.firstValue
        assertThat(u.userType).isEqualTo("guest")
        assertThat(u.faithTone).isEqualTo("balanced")
        assertThat(u.hapticIntensity).isEqualTo("medium")
        assertThat(u.dataRetentionDays).isEqualTo(90)
        assertThat(u.skipIntroSilence).isFalse()
        verify(devices).save(any())
        verify(jwt).issue(r.userId, "quest3")
    }

    @Test
    fun `issueGuest 기존디바이스 사용자재사용 lastSeen갱신`() {
        val uc = IssueGuestTokenUseCase(users, devices, jwt)
        val existingUser = UUID.randomUUID()
        val dev = Device(
            UUID.randomUUID(), existingUser, "quest3", "fp-old",
            OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusDays(1),
        )
        whenever(devices.findByDeviceFingerprint("fp-old")).thenReturn(Optional.of(dev))
        whenever(devices.save(any())).thenAnswer { it.getArgument(0) }
        whenever(jwt.issue(any(), any())).thenReturn(JwtIssuer.IssuedToken("tok2", Instant.now()))

        val r = uc.execute("fp-old", "visionpro")

        assertThat(r.userId).isEqualTo(existingUser)
        // lastSeen 갱신된 새 Device 가 저장됨 (원본보다 이후 시각).
        val devCap = argumentCaptor<Device>()
        verify(devices).save(devCap.capture())
        assertThat(devCap.firstValue.lastSeenAt).isAfter(dev.lastSeenAt)
        assertThat(devCap.firstValue.userId).isEqualTo(existingUser)
        verify(users, never()).save(any()) // 사용자 재사용 — 신규 INSERT 없음.
        verify(jwt).issue(existingUser, "visionpro")
    }

    @Test
    fun `issueGuest fingerprint null 익명사용자 생성`() {
        val uc = IssueGuestTokenUseCase(users, devices, jwt)
        whenever(users.save(any())).thenAnswer { it.getArgument(0) }
        whenever(devices.save(any())).thenAnswer { it.getArgument(0) }
        whenever(jwt.issue(any(), any())).thenReturn(JwtIssuer.IssuedToken("tok3", Instant.now()))

        val r = uc.execute(null, "web")

        assertThat(r.userId).isNotNull()
        verify(devices, never()).findByDeviceFingerprint(any())
        verify(users).save(any())
    }

    // ─────────────────────────── GetCurrentUserUseCase ───────────────────────────

    @Test
    fun `getCurrentUser 존재하면 반환`() {
        val uc = GetCurrentUserUseCase(users)
        val uid = UUID.randomUUID()
        val u = userWithId(uid)
        whenever(users.findById(uid)).thenReturn(Optional.of(u))

        assertThat(uc.execute(uid)).isSameAs(u)
    }

    @Test
    fun `getCurrentUser 없으면 E_AUTH_REQUIRED`() {
        val uc = GetCurrentUserUseCase(users)
        val uid = UUID.randomUUID()
        whenever(users.findById(uid)).thenReturn(Optional.empty())

        assertThatThrownBy { uc.execute(uid) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_AUTH_REQUIRED)
    }

    // ─────────────────────────── AcceptDisclaimerUseCase ───────────────────────────

    @Test
    fun `acceptDisclaimer 사용자 갱신 및 audit ip해시`() {
        val uc = AcceptDisclaimerUseCase(users, acceptances)
        val uid = UUID.randomUUID()
        whenever(users.findById(uid)).thenReturn(Optional.of(userWithId(uid)))
        whenever(users.save(any())).thenAnswer { it.getArgument(0) }

        val r = uc.execute(uid, "Mozilla/5.0", "203.0.113.7")

        assertThat(r.version).isEqualTo(AcceptDisclaimerUseCase.CURRENT_VERSION)
        // 저장된 User 에 disclaimer 동의 시각/버전이 채워짐.
        val userCap = argumentCaptor<User>()
        verify(users).save(userCap.capture())
        assertThat(userCap.firstValue.disclaimerAcceptedAt).isNotNull()
        assertThat(userCap.firstValue.disclaimerVersion).isEqualTo(AcceptDisclaimerUseCase.CURRENT_VERSION)

        val cap = argumentCaptor<DisclaimerAcceptance>()
        verify(acceptances).save(cap.capture())
        val a = cap.firstValue
        assertThat(a.userId).isEqualTo(uid)
        assertThat(a.userAgent).isEqualTo("Mozilla/5.0")
        // raw IP 저장 금지 — SHA-256 hex (64자) 만.
        assertThat(a.ipHash).hasSize(64).doesNotContain("203.0.113.7")
    }

    @Test
    fun `acceptDisclaimer 긴userAgent 255자 절단 null ip는 null해시`() {
        val uc = AcceptDisclaimerUseCase(users, acceptances)
        val uid = UUID.randomUUID()
        whenever(users.findById(uid)).thenReturn(Optional.of(userWithId(uid)))
        whenever(users.save(any())).thenAnswer { it.getArgument(0) }
        val longUa = "x".repeat(400)

        uc.execute(uid, longUa, null)

        val cap = argumentCaptor<DisclaimerAcceptance>()
        verify(acceptances).save(cap.capture())
        assertThat(cap.firstValue.userAgent).hasSize(255)
        assertThat(cap.firstValue.ipHash).isNull()
    }

    @Test
    fun `acceptDisclaimer 사용자없으면 E_AUTH_REQUIRED`() {
        val uc = AcceptDisclaimerUseCase(users, acceptances)
        val uid = UUID.randomUUID()
        whenever(users.findById(uid)).thenReturn(Optional.empty())

        assertThatThrownBy { uc.execute(uid, "ua", "1.2.3.4") }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_AUTH_REQUIRED)
        verify(acceptances, never()).save(any())
    }

    // ─────────────────────────── UpdateSafetyUseCase ───────────────────────────

    @Test
    fun `updateSafety 모든필드 적용`() {
        val uc = UpdateSafetyUseCase(users)
        val uid = UUID.randomUUID()
        val before = OffsetDateTime.now().minusHours(1)
        val u = User(
            uid, LocalDateTime.now(), before,
            "guest", null, null, null, null, null, null, null, null, null, false,
        )
        whenever(users.findById(uid)).thenReturn(Optional.of(u))
        whenever(users.save(any())).thenAnswer { it.getArgument(0) }

        val patch = UpdateSafetyUseCase.Patch("high", true, "strong", "spiritual", 30, true)
        val result = uc.execute(uid, patch)

        assertThat(result.hapticIntensity).isEqualTo("high")
        assertThat(result.skipIntroSilence).isTrue()
        assertThat(result.faithTone).isEqualTo("strong")
        assertThat(result.preferredMode).isEqualTo("spiritual")
        assertThat(result.dataRetentionDays).isEqualTo(30)
        assertThat(result.aiOptOut).isTrue()
        assertThat(result.updatedAt).isAfter(before)
    }

    @Test
    fun `updateSafety 모두null updatedAt만 갱신 기존필드유지`() {
        val uc = UpdateSafetyUseCase(users)
        val uid = UUID.randomUUID()
        val u = User(
            uid, LocalDateTime.now(), OffsetDateTime.now().minusHours(1),
            "guest", null, "soft", null, "low", null, null, null, null, null, false,
        )
        whenever(users.findById(uid)).thenReturn(Optional.of(u))
        whenever(users.save(any())).thenAnswer { it.getArgument(0) }

        val patch = UpdateSafetyUseCase.Patch(null, null, null, null, null, null)
        val result = uc.execute(uid, patch)

        // null 패치는 기존 값 보존.
        assertThat(result.hapticIntensity).isEqualTo("low")
        assertThat(result.faithTone).isEqualTo("soft")
        assertThat(result.updatedAt).isNotNull()
    }

    @Test
    fun `updateSafety 사용자없으면 E_AUTH_REQUIRED`() {
        val uc = UpdateSafetyUseCase(users)
        val uid = UUID.randomUUID()
        whenever(users.findById(uid)).thenReturn(Optional.empty())

        assertThatThrownBy {
            uc.execute(uid, UpdateSafetyUseCase.Patch(null, null, null, null, null, null))
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_AUTH_REQUIRED)
    }
}
