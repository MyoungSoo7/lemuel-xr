package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime
import java.util.UUID

/** AR 토픽 카드 북마크 도메인 모델 — 불변. userId 는 익명 게스트 포함(익명 우선 P1). */
data class CardBookmark(
    val id: UUID?,
    val userId: UUID?,
    val topicContentId: Long,
    val createdAt: LocalDateTime?,
)
