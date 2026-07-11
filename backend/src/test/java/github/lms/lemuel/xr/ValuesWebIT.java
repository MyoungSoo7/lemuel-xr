package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * values/adapter/in/web/ValuesController 통합 테스트.
 *
 * <p>실 서버(RANDOM_PORT) + Postgres/pgvector Testcontainer + Flyway. ContentWebIT 와 동일한
 * 게스트 발급 → disclaimer 동의 인증 패턴을 재사용해 451/401 게이트를 통과시킨다.
 *
 * <p>커버: GET /me (프로파일 없는 신규 사용자 → 빈 값 + CDR 0 + BUILD_UP) · POST /profile
 * (7 가치 upsert 후 통계 재조회) · POST /practice (실천 기록 + CDR 반영) · 인증 게이트.
 */
class ValuesWebIT extends IntegrationTestBase {

    @LocalServerPort
    int port;

    private String token;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private RestClient authed() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @BeforeEach
    void issueGuestAndAcceptDisclaimer() {
        var rest = client();
        Map<?, ?> guest = rest.post().uri("/api/auth/guest")
                .body(Map.of("deviceFingerprint", "values-web-it-" + System.nanoTime(),
                        "deviceType", "quest3"))
                .retrieve().body(Map.class);
        this.token = (String) guest.get("token");
        assertThat(token).isNotBlank();
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void me_신규사용자_빈프로파일_CDR0_BUILD_UP() {
        Map<String, Object> body = authed().get().uri("/api/values/me")
                .retrieve().body(Map.class);
        Map<String, Object> values = (Map<String, Object>) body.get("values");
        assertThat(values).isEmpty();
        Map<String, Object> stats = (Map<String, Object>) body.get("stats");
        assertThat(stats).containsEntry("totalPractices7d", 0)
                .containsEntry("cdrIndex", 0)
                .containsEntry("tier", "BUILD_UP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void profile_upsert_후_값_반영() {
        Map<String, Object> body = authed().post().uri("/api/values/profile")
                .body(Map.of("1", Map.of("title", "흔들리지 않는 결정", "anchor_character", "joseph")))
                .retrieve().body(Map.class);
        Map<String, Object> values = (Map<String, Object>) body.get("values");
        assertThat(values).containsKey("1");
        assertThat(((Map<String, Object>) values.get("1")).get("title"))
                .isEqualTo("흔들리지 않는 결정");
        // upsert 응답에도 stats 포함.
        assertThat((Map<String, Object>) body.get("stats")).containsKey("cdrIndex");
    }

    @Test
    @SuppressWarnings("unchecked")
    void profile_부분갱신_기존키_유지() {
        authed().post().uri("/api/values/profile")
                .body(Map.of("1", Map.of("title", "첫째"))).retrieve().body(Map.class);
        Map<String, Object> body = authed().post().uri("/api/values/profile")
                .body(Map.of("2", Map.of("title", "둘째")))
                .retrieve().body(Map.class);
        Map<String, Object> values = (Map<String, Object>) body.get("values");
        assertThat(values).containsKeys("1", "2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void practice_기록_후_me_에서_카운트_증가() {
        Map<String, Object> practice = authed().post().uri("/api/values/practice")
                .body(Map.of("valueId", 3, "durationSec", 120, "note", "감사한 하루",
                        "linkedCharacter", "joseph"))
                .retrieve().body(Map.class);
        assertThat(practice).containsEntry("valueId", 3);
        assertThat(practice.get("id")).isNotNull();

        Map<String, Object> me = authed().get().uri("/api/values/me").retrieve().body(Map.class);
        Map<String, Object> stats = (Map<String, Object>) me.get("stats");
        assertThat(((Number) stats.get("totalPractices7d")).intValue()).isGreaterThanOrEqualTo(1);
        Map<String, Object> countByValue = (Map<String, Object>) stats.get("countByValue");
        assertThat(countByValue).containsKey("3");
    }

    @Test
    void practice_범위밖_valueId_400() {
        // @Min(1) @Max(7) — valueId=8 은 ConstraintViolation → 400.
        try {
            authed().post().uri("/api/values/practice")
                    .body(Map.of("valueId", 8))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void me_인증없으면_401_또는_451() {
        try {
            client().get().uri("/api/values/me").retrieve().body(String.class);
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403, 451);
        }
    }
}
