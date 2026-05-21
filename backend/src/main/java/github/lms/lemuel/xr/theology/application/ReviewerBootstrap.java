package github.lms.lemuel.xr.theology.application;

import jakarta.annotation.PostConstruct;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자문가 (reviewer_profiles) 부팅 시 idempotent 시드.
 *
 * <p>왜 Flyway 가 아닌가 — {@code docs/governance/REVIEWER-BOOTSTRAP.md §0}.
 * PII / 시점 의존성 / user_id 결정 어려움 / GDPR 삭제 4가지 이유로 마이그레이션 회피.
 *
 * <p>활성화 — {@code lemuel.bootstrap.reviewers.enabled=true} + reviewers 목록 yml.
 * 예: K8s Secret 으로 {@code application-bootstrap.yml} + {@code reviewers.yml} 주입.
 *
 * <p>매 부팅 시 ON CONFLICT DO UPDATE 패턴으로 idempotent — 재배포 안전.
 * 자문가 사임은 yml 에서 entry 제거 + 별도 deactivate (자동 비활성화 안 함 — audit 보존).
 *
 * <p>대상 — {@code reviewer_profiles} 만 시드. {@code users} 의 가입 자체는 자문가가
 * 직접 OAuth 수행. 매칭은 {@code users.external_id} 기준.
 *
 * <p>관련: V20260521040956 (reviewer_profiles), V20260521135130 (theology_reviews 정렬),
 * Issue #4 (임상자문 영입), docs/governance/CLINICAL-REVIEW.md.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lemuel.bootstrap.reviewers", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ReviewerBootstrap.ReviewersConfig.class)
public class ReviewerBootstrap {

    private final ReviewersConfig config;
    private final JdbcTemplate jdbc;

    @PostConstruct
    @Transactional
    public void seed() {
        List<ReviewerEntry> reviewers = config.getReviewers();
        if (reviewers == null || reviewers.isEmpty()) {
            log.info("reviewer bootstrap enabled — but reviewers list 비어있음 (skip)");
            return;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (ReviewerEntry e : reviewers) {
            // 1) external_id → user_id 매칭 (자문가가 OAuth 가입 안 했으면 skip)
            UUID userId = findUserIdByExternalId(e.getExternalId());
            if (userId == null) {
                log.warn(
                    "reviewer bootstrap — external_id '{}' 의 users row 없음. 자문가 OAuth 가입 필요. (skip)",
                    e.getExternalId());
                skipped++;
                continue;
            }

            // 2) ON CONFLICT (user_id, role) DO UPDATE 패턴
            String scopesJson =
                e.getReviewScopes() == null ? "[]" : toJsonArray(e.getReviewScopes());

            int affected = jdbc.update(
                """
                INSERT INTO reviewer_profiles
                    (user_id, role, credential, organization, bio,
                     is_active, can_veto, review_scopes,
                     activated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, TRUE, ?, ?::jsonb, NOW(), NOW(), NOW())
                ON CONFLICT (user_id, role) DO UPDATE
                SET credential   = EXCLUDED.credential,
                    organization = EXCLUDED.organization,
                    bio          = EXCLUDED.bio,
                    can_veto     = EXCLUDED.can_veto,
                    review_scopes = EXCLUDED.review_scopes,
                    -- 비활성된 자문가를 yml 에 다시 넣으면 재활성. 의도된 동작.
                    is_active    = TRUE,
                    deactivated_at = NULL,
                    updated_at   = NOW()
                """,
                ps -> {
                    ps.setObject(1, userId);
                    ps.setString(2, e.getRole());
                    ps.setString(3, e.getCredential());
                    ps.setString(4, e.getOrganization());
                    ps.setString(5, e.getBio());
                    ps.setBoolean(6, Boolean.TRUE.equals(e.getCanVeto()));
                    ps.setString(7, scopesJson);
                });

            if (affected == 1) {
                inserted++;
                log.info(
                    "reviewer bootstrap — INSERT role={} external_id={} scopes={}",
                    e.getRole(), e.getExternalId(), scopesJson);
            } else {
                updated++;
                log.info(
                    "reviewer bootstrap — UPDATE role={} external_id={}",
                    e.getRole(), e.getExternalId());
            }
        }

        log.info(
            "reviewer bootstrap 완료 — inserted={} updated={} skipped={}",
            inserted, updated, skipped);
    }

    private UUID findUserIdByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        try {
            return jdbc.queryForObject(
                "SELECT id FROM users WHERE external_id = ?",
                (rs, n) -> (UUID) rs.getObject(1),
                externalId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * scopes 가 List&lt;String&gt; 이므로 단순 JSON array 직렬화. ObjectMapper 안 끌어다 씀
     * (의존 최소).
     */
    private static String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(values.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    // ======================================================================
    // Configuration properties
    // ======================================================================

    @Configuration
    @ConfigurationProperties(prefix = "lemuel.bootstrap.reviewers")
    @Data
    public static class ReviewersConfig {
        private boolean enabled;
        private List<ReviewerEntry> reviewers;
    }

    @Data
    public static class ReviewerEntry {
        /** users.external_id (예: oauth-google|user@example.com) */
        private String externalId;

        /** 'theology' | 'clinical' | 'ethics' | 'editorial' */
        private String role;

        /** 자격·소속 — 자문가가 동의한 공개 범위 */
        private String credential;
        private String organization;
        private String bio;

        /** veto 단독 reject 권한. 임상 자문 default TRUE, 신학 자문 default FALSE. */
        private Boolean canVeto;

        /** 검토 범위 (Theme / trigger level 등). 예: ["theme_5","theme_11","trigger_high"] */
        private List<String> reviewScopes;
    }
}
