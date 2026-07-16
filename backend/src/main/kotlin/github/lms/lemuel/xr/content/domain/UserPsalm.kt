package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime
import java.util.UUID

/** Theme 4 사용자 시편 도메인 모델 — 불변. */
data class UserPsalm(
    val id: UUID?,
    val userId: UUID?,
    val psalmForm: String?,
    val rawText: String?,
    val polishedText: String?,
    val acceptedPolished: Boolean?,
    val inspiredByPsalm: String?,
    val createdAt: LocalDateTime?,
)
