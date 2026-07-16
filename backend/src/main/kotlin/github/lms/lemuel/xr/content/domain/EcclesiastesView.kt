package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime
import java.util.UUID

/** 기준4 전도서 view 도메인 모델 — 불변. */
data class EcclesiastesView(
    val id: Long?,
    val userId: UUID?,
    val chapterRef: String?,
    val userSeason: String?,
    val futilityNote: String?,
    val meaningNote: String?,
    val listenedAudio: Boolean?,
    val conclusionViewed: Boolean?,
    val createdAt: LocalDateTime?,
)
