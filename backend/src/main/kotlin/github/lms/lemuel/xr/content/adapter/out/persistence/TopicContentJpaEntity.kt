package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import java.time.OffsetDateTime

/** topic_contents (V20260522014700) — AR 1~7 큐레이션 카드. */
@Entity
@Table(name = "topic_contents")
class TopicContentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "topic_id", nullable = false)
    var topicId: Short? = null

    @Column(name = "title", nullable = false, length = 200)
    var title: String? = null

    @Column(name = "scripture_ref", length = 50)
    var scriptureRef: String? = null

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    var body: String? = null

    @Column(name = "anchor_character", length = 20)
    var anchorCharacter: String? = null

    @Column(name = "curator", nullable = false, length = 50)
    var curator: String? = null

    @Column(name = "published_at", nullable = false)
    var publishedAt: OffsetDateTime? = null

    @Column(name = "target_emotion", length = 30)
    var targetEmotion: String? = null

    @Column(name = "difficulty")
    var difficulty: Short? = null

    @Column(name = "active", nullable = false)
    var active: Boolean? = true
}
