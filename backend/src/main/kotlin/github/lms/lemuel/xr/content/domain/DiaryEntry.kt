package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime
import java.util.UUID

/** Theme 1 일기 도메인 모델 — 불변. 영속 상세(암호화 컬럼 등)는 어댑터에만 존재. */
data class DiaryEntry(
    val id: UUID?,
    val userId: UUID?,
    val body: String?,
    val formType: String?,
    val emotionLabel: String?,
    val intensity: Short?,
    val wordCount: Int?,
    val meditationText: String?,
    val meditationAccepted: Boolean?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
