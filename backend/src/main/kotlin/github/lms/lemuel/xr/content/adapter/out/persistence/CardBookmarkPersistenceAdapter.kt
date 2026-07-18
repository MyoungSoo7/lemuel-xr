package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.CardBookmarkPort
import github.lms.lemuel.xr.content.domain.BookmarkedCard
import github.lms.lemuel.xr.content.domain.CardBookmark
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CardBookmarkPersistenceAdapter(
    private val repository: CardBookmarkJpaRepository,
    private val topicContents: TopicContentJpaRepository,
) : CardBookmarkPort {

    override fun topicContentExists(topicContentId: Long): Boolean =
        topicContents.existsById(topicContentId)

    override fun find(userId: UUID, topicContentId: Long): CardBookmark? =
        repository.findByUserIdAndTopicContentId(userId, topicContentId)?.let(::toDomain)

    override fun save(bookmark: CardBookmark): CardBookmark =
        toDomain(repository.save(toEntity(bookmark)))

    override fun delete(userId: UUID, topicContentId: Long): Long =
        repository.deleteByUserIdAndTopicContentId(userId, topicContentId)

    override fun listBookmarkedCards(userId: UUID): List<BookmarkedCard> =
        repository.findBookmarkedCards(userId).map {
            BookmarkedCard(
                topicContentId = it.topicContentId,
                topicId = it.topicId,
                title = it.title,
                scriptureRef = it.scriptureRef,
                body = it.body,
                anchorCharacter = it.anchorCharacter,
                bookmarkedAt = it.bookmarkedAt,
            )
        }

    private fun toDomain(e: CardBookmarkJpaEntity): CardBookmark =
        CardBookmark(e.id, e.userId, e.topicContentId!!, e.createdAt)

    private fun toEntity(d: CardBookmark): CardBookmarkJpaEntity =
        CardBookmarkJpaEntity().apply {
            id = d.id
            userId = d.userId
            topicContentId = d.topicContentId
            createdAt = d.createdAt
        }
}
