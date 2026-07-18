package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.BookmarkedCard
import github.lms.lemuel.xr.content.domain.CardBookmark
import java.util.UUID

/** AR 토픽 카드 북마크 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface CardBookmarkPort {

    /** 북마크 대상 카드가 존재하는지 (잘못된 topicContentId 방어). */
    fun topicContentExists(topicContentId: Long): Boolean

    /** 이미 북마크돼 있으면 반환, 없으면 null (멱등 add 용). */
    fun find(userId: UUID, topicContentId: Long): CardBookmark?

    fun save(bookmark: CardBookmark): CardBookmark

    /** 삭제된 행 수 (0 = 원래 없었음). */
    fun delete(userId: UUID, topicContentId: Long): Long

    /** '내 북마크' 목록 — 최신순 + 카드 표시정보 조인. */
    fun listBookmarkedCards(userId: UUID): List<BookmarkedCard>
}
