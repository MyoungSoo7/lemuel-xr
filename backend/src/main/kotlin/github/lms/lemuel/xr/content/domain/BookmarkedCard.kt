package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime

/**
 * '내 북마크' 목록 읽기 모델 — 북마크 + 대상 카드(topic_contents) 조인 뷰.
 * 목록에서 카드 원문(scriptureRef)까지 바로 열 수 있도록 카드 표시 정보를 포함.
 */
data class BookmarkedCard(
    val topicContentId: Long,
    val topicId: Short?,
    val title: String,
    val scriptureRef: String?,
    val body: String,
    val anchorCharacter: String?,
    val bookmarkedAt: LocalDateTime,
)
