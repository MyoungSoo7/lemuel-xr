package github.lms.lemuel.xr.theology.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * clinical_reviews 테이블 매핑 — V20260521040956.
 *
 * <p>theology_reviews 와 *병렬* 구조. 양쪽 모두 verdict='approve' 여야 content_versions
 * 가 published.
 *
 * <p>moral_injury_risk 가 1~2 면 veto 후보 — Jones 2022 PMID 35609469 의 moral injury
 * 메커니즘 직접 매핑.
 */
@Entity
@Table(name = "clinical_reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ClinicalReviewJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private UUID contentVersionId;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewer_profile_id")
    private UUID reviewerProfileId;

    @Column(nullable = false, length = 20)
    private String verdict;     // 'approve' | 'request_changes' | 'reject'

    // 임상 체크리스트 1-5 score
    @Column(name = "trauma_safety")
    private Short traumaSafety;

    @Column(name = "crisis_resource_compliance")
    private Short crisisResourceCompliance;

    @Column(name = "moral_injury_risk")
    private Short moralInjuryRisk;

    @Column(name = "evidence_quality")
    private Short evidenceQuality;

    // Veto — 단독 reject 권한 (F-7.5.4)
    @Column(name = "veto_used", nullable = false)
    private Boolean vetoUsed = Boolean.FALSE;

    @Column(name = "veto_reason", columnDefinition = "text")
    private String vetoReason;

    @Column(columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "referenced_pmids", columnDefinition = "jsonb")
    private List<String> referencedPmids;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_changes", columnDefinition = "jsonb")
    private Map<String, Object> suggestedChanges;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
