package github.lms.lemuel.xr.content.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Theme 6 (마음 지킴) · 7 (사람 두려움) 실천/성찰 기록.
 *
 * <p>TRACK-A-5-7-ACTION-GUIDANCE §3·§4. proverbs_interactions 와 동일한 JSONB 성찰 패턴.
 * topic_id (6|7) + practice_kind 로 실천 종류 구분. 성경만 근거 — 외부 자료 필드 없음.
 */
@Entity
@Table(name = "practice_reflections")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PracticeReflectionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 6 = 마음 지킴, 7 = 사람 두려움. */
    @Column(name = "topic_id", nullable = false)
    private Short topicId;

    /** 'heart_checkin'|'boundary_sentence'|'courage_act'|'thought_record'. */
    @Column(name = "practice_kind", nullable = false, length = 30)
    private String practiceKind;

    @Column(columnDefinition = "text")
    private String situation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reflection", columnDefinition = "jsonb")
    private Map<String, Object> reflection;

    /** §7 측정 — 경계 문장 전송·작은 행동 실행 체크. */
    @Column(name = "action_taken", nullable = false)
    private Boolean actionTaken = false;

    /** 성경 카드 참조. 성경 외 근거 없음. */
    @Column(name = "scripture_ref", length = 50)
    private String scriptureRef;

    /** 'spiritual'|'emotional'|'rational'. */
    @Column(length = 20)
    private String dimension;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
