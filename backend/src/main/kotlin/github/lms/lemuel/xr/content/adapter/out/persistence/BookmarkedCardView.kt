package github.lms.lemuel.xr.content.adapter.out.persistence

import java.time.LocalDateTime

/**
 * JPQL 생성자 표현식 대상 — card_bookmarks ⨝ topic_contents 조인 결과.
 * 영속 계층 전용. 어댑터가 도메인 [github.lms.lemuel.xr.content.domain.BookmarkedCard] 로 매핑.
 */
class BookmarkedCardView(
    val topicContentId: Long,
    val topicId: Short?,
    val title: String,
    val scriptureRef: String?,
    val body: String,
    val anchorCharacter: String?,
    val bookmarkedAt: LocalDateTime,
)
