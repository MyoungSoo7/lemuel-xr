package github.lms.lemuel.xr.theology.application;

import github.lms.lemuel.xr.theology.adapter.out.persistence.ClinicalReviewJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ClinicalReviewRepository;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentQuarantineJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentQuarantineRepository;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentVersionJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.ContentVersionRepository;
import github.lms.lemuel.xr.theology.adapter.out.persistence.TheologyReviewJpaEntity;
import github.lms.lemuel.xr.theology.adapter.out.persistence.TheologyReviewRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신학·임상 검토 결과를 종합하여 {@link ContentVersionJpaEntity#getStatus()} 자동 전환.
 *
 * <p>FUNCTIONAL-SPEC §F-7.5.4 / §F-7.5.7 / CLINICAL-REVIEW.md §3 흐름 구현.
 *
 * <p>결정 로직 (최근 review 기준):
 * <pre>
 *   1. clinical Veto 사용  → 'rejected'                (단독, 신학 verdict 무관)
 *   2. 한쪽이라도 reject   → 'rejected'                (신학 우선 vs 임상 우선은 메타데이터)
 *   3. 양쪽 모두 approve   → 'published' (+ published_at)
 *   4. 한쪽이라도 request_changes → 'changes_requested'
 *   5. 한쪽 verdict 만 있음 → 변경 없음 (다른 자문 대기)
 * </pre>
 *
 * <p>같은 reviewer 가 다시 verdict 등록하면 *최근 row* 가 의사결정에 사용 — 재검토 후
 * 결정 가능. 다만 *이미 published 인 콘텐츠* 는 본 use case 의 입력으로 들어와도
 * status 변경 안 함 (immutability 가까운 보호 — 새 version 으로 superseded 권장).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvaluateContentStatusUseCase {

    private final ContentVersionRepository versions;
    private final TheologyReviewRepository theologyReviews;
    private final ClinicalReviewRepository clinicalReviews;
    private final ContentQuarantineRepository quarantines;

    @Transactional
    public Decision execute(UUID contentVersionId) {
        ContentVersionJpaEntity v = versions.findById(contentVersionId).orElse(null);
        if (v == null) return new Decision(contentVersionId, null, null, "content_not_found");

        // published / archived 콘텐츠는 본 흐름에서 변경 X
        if ("published".equals(v.getStatus()) || "archived".equals(v.getStatus())) {
            return new Decision(contentVersionId, v.getStatus(), v.getStatus(), "terminal_state");
        }

        Optional<TheologyReviewJpaEntity> theology = latestTheology(contentVersionId);
        Optional<ClinicalReviewJpaEntity> clinical = latestClinical(contentVersionId);

        String previous = v.getStatus();
        String next = decideNext(theology, clinical);

        if (next != null && !next.equals(previous)) {
            v.setStatus(next);
            if ("published".equals(next) && v.getPublishedAt() == null) {
                v.setPublishedAt(LocalDateTime.now());
            }
            versions.save(v);
            log.info("content_version {} status: {} → {} (theology={}, clinical={})",
                    contentVersionId, previous, next,
                    theology.map(TheologyReviewJpaEntity::getVerdict).orElse("none"),
                    clinical.map(ClinicalReviewJpaEntity::getVerdict).orElse("none"));

            // B — rejected 전환 시 content_quarantine 자동 적재
            if ("rejected".equals(next)) {
                quarantineRejected(contentVersionId, theology, clinical);
            }
        }
        return new Decision(contentVersionId, previous, v.getStatus(), reasonOf(theology, clinical, next));
    }

    /** Veto / reject 시 content_quarantine 자동 적재 (idempotent — 같은 veto_by 두 번 안 만듦). */
    private void quarantineRejected(UUID contentVersionId,
                                     Optional<TheologyReviewJpaEntity> t,
                                     Optional<ClinicalReviewJpaEntity> c) {
        String vetoBy;
        String reason;
        Long theologyReviewId = null;
        Long clinicalReviewId = null;

        if (c.isPresent() && Boolean.TRUE.equals(c.get().getVetoUsed())) {
            vetoBy = "clinical_veto";
            reason = c.get().getVetoReason() != null ? c.get().getVetoReason() : "(no reason)";
            clinicalReviewId = c.get().getId();
        } else if (t.isPresent() && "reject".equals(t.get().getVerdict())
                && c.isPresent() && "reject".equals(c.get().getVerdict())) {
            vetoBy = "both_reject";
            reason = String.format("theology: %s | clinical: %s",
                    safeNotes(t.get().getNotes()), safeNotes(c.get().getNotes()));
            theologyReviewId = t.get().getId();
            clinicalReviewId = c.get().getId();
        } else if (t.isPresent() && "reject".equals(t.get().getVerdict())) {
            vetoBy = "theology_reject";
            reason = safeNotes(t.get().getNotes());
            theologyReviewId = t.get().getId();
        } else if (c.isPresent() && "reject".equals(c.get().getVerdict())) {
            vetoBy = "clinical_reject";
            reason = safeNotes(c.get().getNotes());
            clinicalReviewId = c.get().getId();
        } else {
            return; // 이 분기 도달 X — defensive
        }

        // Idempotent — 같은 (content_version_id, veto_by) 가 이미 있으면 skip
        if (quarantines.existsByContentVersionIdAndVetoBy(contentVersionId, vetoBy)) {
            log.info("content_quarantine 이미 적재됨 — skip. version={} veto_by={}",
                    contentVersionId, vetoBy);
            return;
        }

        ContentQuarantineJpaEntity q = new ContentQuarantineJpaEntity();
        q.setContentVersionId(contentVersionId);
        q.setVetoBy(vetoBy);
        q.setReason(reason);
        q.setBlockedKeywords(List.of());     // 자동 키워드 필터 통합 시 채워질 자리
        q.setTriggeredByTheologyReviewId(theologyReviewId);
        q.setTriggeredByClinicalReviewId(clinicalReviewId);
        q.setQuarantinedAt(LocalDateTime.now());
        quarantines.save(q);

        log.warn("content_quarantine 적재 — version={} veto_by={}", contentVersionId, vetoBy);
    }

    private static String safeNotes(String notes) {
        return notes == null || notes.isBlank() ? "(no notes)" : notes;
    }

    private String decideNext(Optional<TheologyReviewJpaEntity> t, Optional<ClinicalReviewJpaEntity> c) {
        // 1. clinical Veto 사용 → 즉시 rejected (신학 verdict 무관)
        if (c.isPresent() && Boolean.TRUE.equals(c.get().getVetoUsed())) {
            return "rejected";
        }
        // 2. 한쪽이라도 reject
        if (t.isPresent() && "reject".equals(t.get().getVerdict())) return "rejected";
        if (c.isPresent() && "reject".equals(c.get().getVerdict())) return "rejected";

        // 3. 양쪽 모두 approve → published
        if (t.isPresent() && "approve".equals(t.get().getVerdict())
                && c.isPresent() && "approve".equals(c.get().getVerdict())) {
            return "published";
        }
        // 4. 한쪽이라도 request_changes
        if ((t.isPresent() && "request_changes".equals(t.get().getVerdict()))
                || (c.isPresent() && "request_changes".equals(c.get().getVerdict()))) {
            return "changes_requested";
        }
        // 5. 한쪽 verdict 만 있음 / 둘 다 없음 → 변경 안 함
        return null;
    }

    private Optional<TheologyReviewJpaEntity> latestTheology(UUID contentVersionId) {
        List<TheologyReviewJpaEntity> all = theologyReviews.findByContentVersionId(contentVersionId);
        return all.stream().max(Comparator.comparing(TheologyReviewJpaEntity::getReviewedAt));
    }

    private Optional<ClinicalReviewJpaEntity> latestClinical(UUID contentVersionId) {
        List<ClinicalReviewJpaEntity> all = clinicalReviews.findByContentVersionId(contentVersionId);
        return all.stream().max(Comparator.comparing(ClinicalReviewJpaEntity::getReviewedAt));
    }

    private static String reasonOf(Optional<TheologyReviewJpaEntity> t,
                                    Optional<ClinicalReviewJpaEntity> c, String next) {
        if (next == null) return "waiting_other_side";
        if (c.isPresent() && Boolean.TRUE.equals(c.get().getVetoUsed())) return "clinical_veto";
        if ("published".equals(next)) return "both_approved";
        if ("rejected".equals(next)) return "one_side_reject";
        if ("changes_requested".equals(next)) return "changes_requested";
        return next;
    }

    public record Decision(UUID contentVersionId, String previousStatus,
                            String currentStatus, String reason) {}
}
