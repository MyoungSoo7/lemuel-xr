package github.lms.lemuel.xr.auth.application

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.User
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** 사용자 안전·취향 설정 갱신 (haptic·skipSilence·faithTone·preferredMode 등). */
@Service
class UpdateSafetyUseCase(
    private val users: UserPort,
) {

    @Transactional
    fun execute(userId: UUID, patch: Patch): User {
        val u = users.findById(userId)
            .orElseThrow { AppException(ErrorCode.E_AUTH_REQUIRED) }
        // null 패치는 기존 값 보존 (COALESCE 시맨틱).
        val updated = u.copy(
            updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
            faithTone = patch.faithTone ?: u.faithTone,
            preferredMode = patch.preferredMode ?: u.preferredMode,
            hapticIntensity = patch.hapticIntensity ?: u.hapticIntensity,
            skipIntroSilence = patch.skipIntroSilence ?: u.skipIntroSilence,
            dataRetentionDays = patch.dataRetentionDays ?: u.dataRetentionDays,
            aiOptOut = patch.aiOptOut ?: u.aiOptOut,
        )
        return users.save(updated)
    }

    @JvmRecord
    data class Patch(
        val hapticIntensity: String?,
        val skipIntroSilence: Boolean?,
        val faithTone: String?,
        val preferredMode: String?,
        val dataRetentionDays: Int?,
        val aiOptOut: Boolean?,
    )
}
