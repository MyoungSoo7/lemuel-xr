package github.lms.lemuel.xr.theology.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.theology.application.ReviewerBootstrap.ReviewerEntry;
import github.lms.lemuel.xr.theology.application.ReviewerBootstrap.ReviewersConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

/**
 * ReviewerBootstrap 단위 테스트 — JdbcTemplate mock.
 *
 * <p>검증 포인트:
 * <ol>
 *   <li>reviewers 비어있으면 INSERT 호출 0</li>
 *   <li>external_id 매칭 안 되면 skip (INSERT 호출 안 함)</li>
 *   <li>매칭되면 ON CONFLICT INSERT 호출</li>
 *   <li>review_scopes 가 정상 JSON array 로 직렬화</li>
 * </ol>
 */
class ReviewerBootstrapTest {

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    @Test
    void reviewers_가_비어있으면_INSERT_안함() {
        ReviewersConfig config = new ReviewersConfig();
        config.setEnabled(true);
        config.setReviewers(List.of());

        new ReviewerBootstrap(config, jdbc).seed();

        verify(jdbc, never()).update(anyString(), any(PreparedStatementSetter.class));
    }

    @Test
    void external_id_매칭_안되면_skip() {
        ReviewersConfig config = new ReviewersConfig();
        config.setEnabled(true);
        ReviewerEntry e = new ReviewerEntry();
        e.setExternalId("oauth-google|nonexistent@example.com");
        e.setRole("theology");
        e.setCredential("placeholder");
        e.setReviewScopes(List.of("theme_1"));
        config.setReviewers(List.of(e));

        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(e.getExternalId())))
                .thenThrow(new EmptyResultDataAccessException(1));

        new ReviewerBootstrap(config, jdbc).seed();

        // INSERT 미호출 (skip)
        verify(jdbc, never()).update(anyString(), any(PreparedStatementSetter.class));
    }

    @Test
    void 매칭되면_INSERT_호출_그리고_scopes_JSON_직렬화() {
        UUID userId = UUID.randomUUID();

        ReviewersConfig config = new ReviewersConfig();
        config.setEnabled(true);
        ReviewerEntry e = new ReviewerEntry();
        e.setExternalId("oauth-google|owner@example.com");
        e.setRole("theology");
        e.setCredential("운영 책임자");
        e.setOrganization("MyoungSoo7");
        e.setBio("운영 책임자.");
        e.setCanVeto(false);
        e.setReviewScopes(List.of("theme_1", "theme_5", "theme_11"));
        config.setReviewers(List.of(e));

        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(e.getExternalId())))
                .thenReturn(userId);
        when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(1);

        new ReviewerBootstrap(config, jdbc).seed();

        verify(jdbc, times(1)).update(anyString(), any(PreparedStatementSetter.class));
    }

    @Test
    void scopes_JSON_array_직렬화_특수문자_escape() throws Exception {
        // toJsonArray 가 private static 이라 reflection 으로 검증
        java.lang.reflect.Method m =
                ReviewerBootstrap.class.getDeclaredMethod("toJsonArray", List.class);
        m.setAccessible(true);

        String simple = (String) m.invoke(null, List.of("theme_1", "theme_5"));
        assertThat(simple).isEqualTo("[\"theme_1\",\"theme_5\"]");

        String withQuote = (String) m.invoke(null, List.of("scope\"with\"quote"));
        assertThat(withQuote).isEqualTo("[\"scope\\\"with\\\"quote\"]");

        String empty = (String) m.invoke(null, List.of());
        assertThat(empty).isEqualTo("[]");
    }
}
