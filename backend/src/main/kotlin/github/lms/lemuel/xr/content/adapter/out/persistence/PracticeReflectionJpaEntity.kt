package github.lms.lemuel.xr.content.adapter.out.persistence

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

/**
 * Theme 6 (마음 지킴) · 7 (사람 두려움) 실천/성찰 기록.
 *
 * TRACK-A-5-7-ACTION-GUIDANCE §3·§4. proverbs_interactions 와 동일한 JSONB 성찰 패턴.
 * topic_id (6|7) + practice_kind 로 실천 종류 구분. 성경만 근거 — 외부 자료 필드 없음.
 */
@Entity
@Table(name = "practice_reflections")
class PracticeReflectionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    /** 6 = 마음 지킴, 7 = 사람 두려움. */
    @Column(name = "topic_id", nullable = false)
    var topicId: Short? = null

    /** 'heart_checkin'|'boundary_sentence'|'courage_act'|'thought_record'. */
    @Column(name = "practice_kind", nullable = false, length = 30)
    var practiceKind: String? = null

    @Column(columnDefinition = "text")
    var situation: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reflection", columnDefinition = "jsonb")
    var reflection: Map<String, Any?>? = null

    /** §7 측정 — 경계 문장 전송·작은 행동 실행 체크. */
    @Column(name = "action_taken", nullable = false)
    var actionTaken: Boolean? = false

    /** 성경 카드 참조. 성경 외 근거 없음. */
    @Column(name = "scripture_ref", length = 50)
    var scriptureRef: String? = null

    /** 'spiritual'|'emotional'|'rational'. */
    @Column(length = 20)
    var dimension: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
