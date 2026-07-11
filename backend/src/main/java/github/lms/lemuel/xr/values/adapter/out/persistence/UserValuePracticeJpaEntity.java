package github.lms.lemuel.xr.values.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** user_value_practices (V20260522014700) — 일별 실천 로그. CDR Index 계산용. */
@Entity
@Table(name = "user_value_practices")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserValuePracticeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 1~7 — Topic id 와 동일 의미. */
    @Column(name = "value_id", nullable = false)
    private Short valueId;

    @Column(name = "practiced_at", nullable = false)
    private OffsetDateTime practicedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "linked_character", length = 20)
    private String linkedCharacter;

    @Column(name = "linked_game_session")
    private UUID linkedGameSession;
}
