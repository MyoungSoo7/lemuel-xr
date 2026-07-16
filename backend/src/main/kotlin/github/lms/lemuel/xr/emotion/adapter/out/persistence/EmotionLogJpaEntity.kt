package github.lms.lemuel.xr.emotion.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "emotion_logs")
class EmotionLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "app_session_id")
    var appSessionId: UUID? = null

    // raw_text / raw_text_encrypted 컬럼은 docs/safety-guidelines.md §3 (PHI 비수집)
    // 에 의거 *영속화 금지*. 분류 직후 폐기됨. V20260522210000 마이그레이션이 DB 에서도 제거.

    @Column(name = "classified_emotion", length = 30)
    var classifiedEmotion: String? = null

    @Column(precision = 4, scale = 3)
    var confidence: BigDecimal? = null

    @Column
    var intensity: Short? = null

    @Column(name = "chosen_dimension", length = 20)
    var chosenDimension: String? = null

    @Column(name = "recommended_track", length = 1)
    var recommendedTrack: String? = null

    @Column(name = "recommended_content", length = 50)
    var recommendedContent: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
