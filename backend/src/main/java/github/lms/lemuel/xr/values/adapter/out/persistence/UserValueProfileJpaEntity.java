package github.lms.lemuel.xr.values.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** user_value_profiles (V20260522014700) — 사용자별 7 가치 프로파일. */
@Entity
@Table(name = "user_value_profiles")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserValueProfileJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * 7 가치 JSON. 키: "1"~"7" 문자열. value: {title, anchor_character?, anchor_scripture?, note?}.
     * 예: {"1": {"title": "흔들리지 않는 결정", "anchor_character": "joseph", ...}}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "values_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> valuesJson = new HashMap<>();

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;
}
