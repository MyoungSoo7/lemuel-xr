package github.lms.lemuel.xr.theology.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * content_quarantine 매핑 — V20260521173629.
 * 검토 거부 / Veto / 자동 키워드 필터에 걸린 콘텐츠 격리.
 */
@Entity
@Table(name = "content_quarantine")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ContentQuarantineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private UUID contentVersionId;

    /** clinical_veto / theology_reject / clinical_reject / both_reject / auto_keyword_filter / manual */
    @Column(name = "veto_by", nullable = false, length = 32)
    private String vetoBy;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocked_keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> blockedKeywords;

    @Column(name = "triggered_by_theology_review_id")
    private Long triggeredByTheologyReviewId;

    @Column(name = "triggered_by_clinical_review_id")
    private Long triggeredByClinicalReviewId;

    @Column(name = "reviewed_by_admin")
    private UUID reviewedByAdmin;

    @Column(name = "admin_action", length = 32)
    private String adminAction;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @Column(name = "quarantined_at", nullable = false)
    private LocalDateTime quarantinedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
