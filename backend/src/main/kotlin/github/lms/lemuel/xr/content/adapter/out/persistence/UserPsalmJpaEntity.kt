package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user_psalms")
class UserPsalmJpaEntity {
    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "psalm_form", length = 20)
    var psalmForm: String? = null

    @Column(name = "raw_text", columnDefinition = "text")
    var rawText: String? = null

    @Column(name = "raw_text_encrypted")
    var rawTextEncrypted: ByteArray? = null

    @Column(name = "polished_text", columnDefinition = "text")
    var polishedText: String? = null

    @Column(name = "accepted_polished")
    var acceptedPolished: Boolean? = false

    @Column(name = "inspired_by_psalm", length = 20)
    var inspiredByPsalm: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
