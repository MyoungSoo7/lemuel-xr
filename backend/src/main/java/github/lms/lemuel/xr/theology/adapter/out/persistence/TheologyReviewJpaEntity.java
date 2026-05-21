package github.lms.lemuel.xr.theology.adapter.out.persistence;

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

@Entity
@Table(name = "theology_reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class TheologyReviewJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private UUID contentVersionId;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewer_role", nullable = false, length = 20)
    private String reviewerRole;

    @Column(nullable = false, length = 20)
    private String verdict;

    @Column(name = "scripture_accuracy")
    private Short scriptureAccuracy;

    @Column(name = "doctrinal_balance")
    private Short doctrinalBalance;

    @Column(name = "therapeutic_safety")
    private Short therapeuticSafety;

    @Column(columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_changes", columnDefinition = "jsonb")
    private Map<String, Object> suggestedChanges;

    /** V20260521135130 — reviewer_profiles 연결 (자문가 등록 프로필). */
    @Column(name = "reviewer_profile_id")
    private UUID reviewerProfileId;

    /** V20260521135130 — 인용 PMID 배열 (clinical_reviews 와 cross-check). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "referenced_pmids", columnDefinition = "jsonb")
    private java.util.List<String> referencedPmids;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
