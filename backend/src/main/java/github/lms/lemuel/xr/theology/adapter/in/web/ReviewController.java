package github.lms.lemuel.xr.theology.adapter.in.web;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ClinicalReviewJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ClinicalReviewRepository;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentVersionJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentVersionRepository;
import github.lms.lemuel.xr.theology.adapter.out.persistence.TheologyReviewJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.TheologyReviewRepository;
import github.lms.lemuel.xr.theology.application.EvaluateContentStatusUseCase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 자문가 검토 큐 + 결과 등록.
 *
 * <p>최소 viable Phase 2 endpoint — 인증·권한·검토 자격 매칭은 Phase 2-D 에서 보강.
 * 현재는 RequestContext.currentUserId() 가 reviewer_id 로 들어가는 단순 형태.
 *
 * <p>Endpoints:
 * <pre>
 *   GET  /api/reviews/queue                            — 양 role 공통 pending 콘텐츠 list
 *   POST /api/theology/reviews                         — 신학 검토 결과 등록
 *   POST /api/clinical/reviews                         — 임상 검토 결과 등록 (Veto 가능)
 * </pre>
 *
 * <p>Phase 2-D 후속:
 * <ul>
 *   <li>reviewer_profile_id 매칭 (RequestContext + reviewer_profiles join)</li>
 *   <li>리뷰 자격·범위 검증 (review_scopes 와 content_versions.content_kind 매칭)</li>
 *   <li>양쪽 모두 approve 시 자동 status='published' 전환 use case 추출</li>
 *   <li>clinical_reviews.veto_used=TRUE 시 content_quarantine 적재 use case</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ContentVersionRepository versions;
    private final TheologyReviewRepository theologyReviews;
    private final ClinicalReviewRepository clinicalReviews;
    private final EvaluateContentStatusUseCase evaluator;

    @GetMapping("/api/reviews/queue")
    public ResponseEntity<List<QueueItem>> queue() {
        List<ContentVersionJpaEntity> pending = versions.findByStatusOrderByCreatedAtAsc("in_review");
        List<QueueItem> items = pending.stream().map(v -> new QueueItem(
                v.getId(),
                v.getContentKind(),
                v.getContentRef(),
                v.getVersion(),
                v.getCreatedAt(),
                v.getGeneratedBy(),
                hasReview(theologyReviews.findByContentVersionId(v.getId()).size()),
                hasReview(clinicalReviews.findByContentVersionId(v.getId()).size())
        )).toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/api/theology/reviews")
    @Transactional
    public ResponseEntity<ReviewSubmitted> submitTheology(@RequestBody TheologyReviewRequest req) {
        UUID reviewerId = RequestContext.currentUserId();
        if (reviewerId == null) {
            throw new AppException(ErrorCode.E_AUTH_REQUIRED);
        }
        ContentVersionJpaEntity v = versions.findById(req.contentVersionId())
                .orElseThrow(() -> new AppException(ErrorCode.E_CONTENT_VERSION_NOT_FOUND));

        TheologyReviewJpaEntity row = new TheologyReviewJpaEntity();
        row.setContentVersionId(req.contentVersionId());
        row.setReviewerId(reviewerId);
        row.setReviewerProfileId(req.reviewerProfileId());
        row.setReviewerRole("theology");
        row.setVerdict(req.verdict());
        row.setScriptureAccuracy(req.scriptureAccuracy());
        row.setDoctrinalBalance(req.doctrinalBalance());
        row.setTherapeuticSafety(req.therapeuticSafety());
        row.setNotes(req.notes());
        row.setSuggestedChanges(req.suggestedChanges());
        row.setReferencedPmids(req.referencedPmids());
        row.setReviewedAt(LocalDateTime.now());
        theologyReviews.save(row);

        // 양쪽 검토 종합 → 자동 status 전환
        var decision = evaluator.execute(v.getId());

        return ResponseEntity.ok(new ReviewSubmitted(
                row.getId(), v.getId(), row.getReviewedAt(),
                decision.previousStatus(), decision.currentStatus(), decision.reason()));
    }

    @PostMapping("/api/clinical/reviews")
    @Transactional
    public ResponseEntity<ReviewSubmitted> submitClinical(@RequestBody ClinicalReviewRequest req) {
        UUID reviewerId = RequestContext.currentUserId();
        if (reviewerId == null) {
            throw new AppException(ErrorCode.E_AUTH_REQUIRED);
        }
        ContentVersionJpaEntity v = versions.findById(req.contentVersionId())
                .orElseThrow(() -> new AppException(ErrorCode.E_CONTENT_VERSION_NOT_FOUND));

        // Veto 사용 시 사유 필수 (DB CHECK 가 강제, 여기서도 사전 검증)
        if (Boolean.TRUE.equals(req.vetoUsed()) && (req.vetoReason() == null || req.vetoReason().isBlank())) {
            throw new AppException(ErrorCode.E_VALIDATION, "veto_used=true 시 veto_reason 필수");
        }

        ClinicalReviewJpaEntity row = new ClinicalReviewJpaEntity();
        row.setContentVersionId(req.contentVersionId());
        row.setReviewerId(reviewerId);
        row.setReviewerProfileId(req.reviewerProfileId());
        row.setVerdict(req.verdict());
        row.setTraumaSafety(req.traumaSafety());
        row.setCrisisResourceCompliance(req.crisisResourceCompliance());
        row.setMoralInjuryRisk(req.moralInjuryRisk());
        row.setEvidenceQuality(req.evidenceQuality());
        row.setVetoUsed(Boolean.TRUE.equals(req.vetoUsed()));
        row.setVetoReason(req.vetoReason());
        row.setNotes(req.notes());
        row.setReferencedPmids(req.referencedPmids());
        row.setSuggestedChanges(req.suggestedChanges());
        row.setReviewedAt(LocalDateTime.now());
        clinicalReviews.save(row);

        // 양쪽 검토 종합 → 자동 status 전환 (Veto 사용 시 즉시 rejected)
        var decision = evaluator.execute(v.getId());

        return ResponseEntity.ok(new ReviewSubmitted(
                row.getId(), v.getId(), row.getReviewedAt(),
                decision.previousStatus(), decision.currentStatus(), decision.reason()));
    }

    private static boolean hasReview(int count) { return count > 0; }

    // ============= DTOs =============

    public record QueueItem(
            UUID contentVersionId,
            String contentKind,
            String contentRef,
            String version,
            LocalDateTime createdAt,
            String generatedBy,
            boolean theologyReviewed,
            boolean clinicalReviewed
    ) {}

    public record TheologyReviewRequest(
            UUID contentVersionId,
            UUID reviewerProfileId,
            String verdict,                  // approve | request_changes | reject
            Short scriptureAccuracy,         // 1-5
            Short doctrinalBalance,
            Short therapeuticSafety,
            String notes,
            Map<String, Object> suggestedChanges,
            List<String> referencedPmids
    ) {}

    public record ClinicalReviewRequest(
            UUID contentVersionId,
            UUID reviewerProfileId,
            String verdict,
            Short traumaSafety,              // 1-5
            Short crisisResourceCompliance,
            Short moralInjuryRisk,           // 1-5 (낮을수록 위험 — Jones 2022 매핑)
            Short evidenceQuality,
            Boolean vetoUsed,
            String vetoReason,               // vetoUsed=true 시 필수
            String notes,
            Map<String, Object> suggestedChanges,
            List<String> referencedPmids
    ) {}

    public record ReviewSubmitted(
            Long reviewId, UUID contentVersionId, LocalDateTime reviewedAt,
            /** 자동 status 전환 결과 (EvaluateContentStatusUseCase). null 가능 — 콘텐츠 미발견 시. */
            String previousStatus, String currentStatus, String statusReason
    ) {}
}
