package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "diary_entries")
class DiaryEntryJpaEntity {
    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(columnDefinition = "text")
    var body: String? = null

    @Column(name = "body_encrypted")
    var bodyEncrypted: ByteArray? = null

    @Column(name = "form_type", length = 20)
    var formType: String? = null

    @Column(name = "emotion_label", length = 30)
    var emotionLabel: String? = null

    @Column
    var intensity: Short? = null

    @Column(name = "meditation_text", columnDefinition = "text")
    var meditationText: String? = null

    @Column(name = "meditation_accepted")
    var meditationAccepted: Boolean? = false

    @Column(name = "meditation_dimension", length = 20)
    var meditationDimension: String? = null

    @Column(name = "word_count")
    var wordCount: Int? = null

    @Column(name = "sentiment_score", precision = 3, scale = 2)
    var sentimentScore: BigDecimal? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
