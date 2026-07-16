package github.lms.lemuel.xr.values.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

/** user_value_profiles (V20260522014700) — 사용자별 7 가치 프로파일. */
@Entity
@Table(name = "user_value_profiles")
class UserValueProfileJpaEntity {

    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID? = null

    /**
     * 7 가치 JSON. 키: "1"~"7" 문자열. value: {title, anchor_character?, anchor_scripture?, note?}.
     * 예: {"1": {"title": "흔들리지 않는 결정", "anchor_character": "joseph", ...}}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "values_json", columnDefinition = "jsonb", nullable = false)
    var valuesJson: MutableMap<String, Any?> = HashMap()

    @Column(name = "started_at", nullable = false)
    var startedAt: OffsetDateTime? = null

    @Column(name = "last_updated_at", nullable = false)
    var lastUpdatedAt: OffsetDateTime? = null
}
