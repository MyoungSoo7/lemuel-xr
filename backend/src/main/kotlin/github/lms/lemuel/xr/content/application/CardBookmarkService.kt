package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.content.application.port.out.CardBookmarkPort
import github.lms.lemuel.xr.content.domain.BookmarkedCard
import github.lms.lemuel.xr.content.domain.CardBookmark
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * AR 토픽 카드 북마크 유스케이스 — 담기(멱등)/빼기(토글)/목록.
 * userId 는 익명 게스트 포함(익명 우선 P1). 포트에만 의존.
 */
@Service
class CardBookmarkService(
    private val bookmarks: CardBookmarkPort,
) {

    /** 담기 — 멱등. 이미 담겨 있으면 기존 반환, 없으면 생성. */
    @Transactional
    fun add(userId: UUID, topicContentId: Long): CardBookmark {
        if (!bookmarks.topicContentExists(topicContentId)) {
            throw AppException(ErrorCode.E_VALIDATION, "unknown topicContentId=$topicContentId")
        }
        return bookmarks.find(userId, topicContentId)
            ?: bookmarks.save(
                CardBookmark(
                    id = UUID.randomUUID(),
                    userId = userId,
                    topicContentId = topicContentId,
                    createdAt = LocalDateTime.now(),
                ),
            )
    }

    /** 빼기 — 토글. 없어도 조용히 성공(멱등). */
    @Transactional
    fun remove(userId: UUID, topicContentId: Long) {
        bookmarks.delete(userId, topicContentId)
    }

    /** '내 북마크' 목록 — 최신순. */
    @Transactional(readOnly = true)
    fun list(userId: UUID): List<BookmarkedCard> =
        bookmarks.listBookmarkedCards(userId)
}
