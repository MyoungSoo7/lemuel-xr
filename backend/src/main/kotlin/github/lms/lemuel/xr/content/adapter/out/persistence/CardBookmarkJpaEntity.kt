package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

/** card_bookmarks (V20260718184855) — AR 토픽 카드 북마크. */
@Entity
@Table(name = "card_bookmarks")
class CardBookmarkJpaEntity {
    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "topic_content_id", nullable = false)
    var topicContentId: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
