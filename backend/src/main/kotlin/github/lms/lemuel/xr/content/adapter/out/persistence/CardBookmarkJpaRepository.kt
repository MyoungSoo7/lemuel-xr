package github.lms.lemuel.xr.content.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CardBookmarkJpaRepository : JpaRepository<CardBookmarkJpaEntity, UUID> {

    fun findByUserIdAndTopicContentId(userId: UUID, topicContentId: Long): CardBookmarkJpaEntity?

    /** 삭제된 행 수 반환 (토글 remove). */
    fun deleteByUserIdAndTopicContentId(userId: UUID, topicContentId: Long): Long

    /** '내 북마크' 목록 — card_bookmarks ⨝ topic_contents, 최신순. */
    @Query(
        "SELECT new github.lms.lemuel.xr.content.adapter.out.persistence.BookmarkedCardView(" +
            "t.id, t.topicId, t.title, t.scriptureRef, t.body, t.anchorCharacter, b.createdAt) " +
            "FROM CardBookmarkJpaEntity b, TopicContentJpaEntity t " +
            "WHERE b.topicContentId = t.id AND b.userId = :userId " +
            "ORDER BY b.createdAt DESC",
    )
    fun findBookmarkedCards(@Param("userId") userId: UUID): List<BookmarkedCardView>
}
