package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "ecclesiastes_views")
class EcclesiastesViewJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "chapter_ref", length = 20)
    var chapterRef: String? = null

    @Column(name = "user_season", length = 20)
    var userSeason: String? = null

    @Column(name = "futility_note", columnDefinition = "text")
    var futilityNote: String? = null

    @Column(name = "meaning_note", columnDefinition = "text")
    var meaningNote: String? = null

    @Column(name = "listened_audio")
    var listenedAudio: Boolean? = false

    @Column(name = "conclusion_viewed")
    var conclusionViewed: Boolean? = false

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
