package github.lms.lemuel.xr.auth.application

import github.lms.lemuel.xr.auth.application.port.out.DevicePort
import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.Device
import github.lms.lemuel.xr.auth.domain.User
import github.lms.lemuel.xr.common.jwt.JwtIssuer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 게스트 사용자 발급 + 디바이스 등록 + JWT.
 *
 * 같은 device_fingerprint 가 다시 와도 새 사용자 만들지 않고 *기존 사용자* 의
 * JWT 만 재발급. 디바이스 재진입 연속성 보장 (BACKEND-API-DESIGN.md §3.1).
 */
@Service
class IssueGuestTokenUseCase(
    private val users: UserPort,
    private val devices: DevicePort,
    private val jwt: JwtIssuer,
) {

    @Transactional
    fun execute(deviceFingerprint: String?, deviceType: String?): Result {
        val userId: UUID
        if (deviceFingerprint != null) {
            // 기존 디바이스 → 사용자 재사용
            val existing = devices.findByDeviceFingerprint(deviceFingerprint)
            if (existing.isPresent) {
                val dev = existing.get()
                userId = dev.userId!!
                val touched = Device(
                    dev.id, dev.userId, dev.deviceType, dev.deviceFingerprint,
                    OffsetDateTime.now(ZoneOffset.UTC), dev.createdAt,
                )
                devices.save(touched)
            } else {
                userId = createUserAndDevice(deviceFingerprint, deviceType)
            }
        } else {
            userId = createUserAndDevice(null, deviceType)
        }
        val issued = jwt.issue(userId, deviceType)
        return Result(userId, issued.token, issued.expiresAt)
    }

    private fun createUserAndDevice(fingerprint: String?, deviceType: String?): UUID {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val nowLocal = LocalDateTime.now(ZoneOffset.UTC)
        val userId = UUID.randomUUID()
        val user = User(
            userId,
            nowLocal,          // users.created_at = TIMESTAMP (V1)
            now,               // users.updated_at = TIMESTAMPTZ (V3)
            "guest",
            null,              // externalId
            "balanced",        // faithTone
            null,              // preferredMode
            "medium",          // hapticIntensity
            false,             // skipIntroSilence
            90,                // dataRetentionDays
            null,              // deletedAt
            null,              // disclaimerAcceptedAt
            null,              // disclaimerVersion
            false,             // aiOptOut
        )
        users.save(user)

        val device = Device(
            UUID.randomUUID(),
            userId,
            deviceType,
            fingerprint,
            now,               // lastSeenAt
            now,               // createdAt
        )
        devices.save(device)

        return userId
    }

    @JvmRecord
    data class Result(val userId: UUID, val token: String, val expiresAt: Instant)
}
