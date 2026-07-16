package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "proverbs_interactions")
class ProverbsInteractionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "user_situation", columnDefinition = "text")
    var userSituation: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_proverbs", columnDefinition = "jsonb")
    var recommendedProverbs: List<Map<String, Any?>>? = null

    @Column(name = "chosen_proverb_ref", length = 20)
    var chosenProverbRef: String? = null

    @Column(name = "chosen_dimension", length = 20)
    var chosenDimension: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reframing_response", columnDefinition = "jsonb")
    var reframingResponse: Map<String, Any?>? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
