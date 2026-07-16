package github.lms.lemuel.xr.values.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/** user_value_practices (V20260522014700) — 일별 실천 로그. CDR Index 계산용. */
@Entity
@Table(name = "user_value_practices")
class UserValuePracticeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    /** 1~7 — Topic id 와 동일 의미. */
    @Column(name = "value_id", nullable = false)
    var valueId: Short? = null

    @Column(name = "practiced_at", nullable = false)
    var practicedAt: OffsetDateTime? = null

    @Column(name = "duration_sec")
    var durationSec: Int? = null

    @Column(name = "note", columnDefinition = "TEXT")
    var note: String? = null

    @Column(name = "linked_character", length = 20)
    var linkedCharacter: String? = null

    @Column(name = "linked_game_session")
    var linkedGameSession: UUID? = null
}
