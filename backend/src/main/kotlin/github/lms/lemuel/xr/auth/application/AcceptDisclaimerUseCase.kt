package github.lms.lemuel.xr.auth.application

import github.lms.lemuel.xr.auth.application.port.out.DisclaimerAcceptancePort
import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.DisclaimerAcceptance
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID

/**
 * "치료 도구 아님 + 위기 자원 + AI 라벨링" 디스클레이머 동의 처리.
 *
 * users.disclaimer_accepted_at 갱신 + disclaimer_acceptances audit row 추가.
 * raw IP 는 저장하지 않고 SHA-256 hash 만 — ETHICS-LEGAL §2.2 / 개인정보보호법 준수.
 */
@Service
class AcceptDisclaimerUseCase(
    private val users: UserPort,
    private val acceptances: DisclaimerAcceptancePort,
) {

    @Transactional
    fun execute(userId: UUID, userAgent: String?, remoteAddr: String?): Result {
        val u = users.findById(userId)
            .orElseThrow { AppException(ErrorCode.E_AUTH_REQUIRED) }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val accepted = u.copy(
            updatedAt = now,
            disclaimerAcceptedAt = now,
            disclaimerVersion = CURRENT_VERSION,
        )
        users.save(accepted)

        val a = DisclaimerAcceptance(
            null,
            userId,
            now,
            CURRENT_VERSION,
            userAgent?.substring(0, minOf(userAgent.length, 255)),
            hashIp(remoteAddr),
        )
        acceptances.save(a)

        return Result(userId, now, CURRENT_VERSION)
    }

    private fun hashIp(ip: String?): String? {
        if (ip.isNullOrBlank()) return null
        return try {
            val h = MessageDigest.getInstance("SHA-256").digest(ip.toByteArray(StandardCharsets.UTF_8))
            HexFormat.of().formatHex(h)
        } catch (e: NoSuchAlgorithmException) {
            null
        }
    }

    @JvmRecord
    data class Result(val userId: UUID, val acceptedAt: OffsetDateTime, val version: String)

    companion object {
        /** 현재 disclaimer 본문 버전 — 본문 변경 시 bump → 모든 사용자 재동의. */
        const val CURRENT_VERSION: String = "1.0"
    }
}
