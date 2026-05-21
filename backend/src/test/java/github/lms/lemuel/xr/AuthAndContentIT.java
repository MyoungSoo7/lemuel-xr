package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 핵심 E2E 시나리오:
 * 1) 게스트 사용자 발급 → JWT
 * 2) /api/users/me (인증 보호)
 * 3) /api/content/topics (공개)
 * 4) /api/safety/crisis-resources (공개 — 한국어 시드)
 * 5) /api/scripture/gen-45:5 (창세기 본문 한국어 UTF-8)
 * 6) /api/game/joseph/start (인증 보호, scenarios/joseph.yml 로드)
 */
class AuthAndContentIT extends IntegrationTestBase {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    @SuppressWarnings("unchecked")
    void guest_token_then_authenticated_paths() {
        var rest = client();

        // 1. 게스트 발급
        Map<String, Object> guest = rest.post().uri("/api/auth/guest")
                .body(Map.of("deviceFingerprint", "it-1", "deviceType", "quest3"))
                .retrieve().body(Map.class);
        assertThat(guest).containsKey("token").containsKey("userId");
        String token = (String) guest.get("token");
        assertThat(token).isNotBlank();

        // 1.5 Disclaimer 동의 — DisclaimerGateFilter 통과를 위해 필수
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);

        // 2. /api/users/me
        Map<String, Object> me = rest.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(Map.class);
        assertThat(me).containsKey("userId").containsEntry("userType", "guest");

        // 3. /api/content/topics — 7주제 + 한국어
        Map<String, Object> topics = rest.get().uri("/api/content/topics")
                .retrieve().body(Map.class);
        List<?> topicList = (List<?>) topics.get("topics");
        assertThat(topicList).hasSize(7);
        assertThat(topics.toString()).contains("일기와 묵상").contains("사람을 두려워하지 않는 것");

        // 4. /api/safety/crisis-resources — V7 시드
        Map<String, Object> crisis = rest.get().uri("/api/safety/crisis-resources?region=KR&locale=ko-KR")
                .retrieve().body(Map.class);
        List<?> resources = (List<?>) crisis.get("resources");
        assertThat(resources).hasSizeGreaterThanOrEqualTo(4);
        assertThat(resources.toString()).contains("1393").contains("자살예방");

        // 5. /api/scripture/gen-45:5 — V2 시드
        Map<String, Object> scripture = rest.get().uri("/api/scripture/gen-45:5")
                .retrieve().body(Map.class);
        assertThat(scripture).containsEntry("reference", "gen-45:5");
        assertThat((String) scripture.get("text")).contains("하나님");

        // 6. /api/game/joseph/start — Scene 1 "파라오의 꿈"
        Map<String, Object> start = rest.post().uri("/api/game/joseph/start")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("mode", "emotional"))
                .retrieve().body(Map.class);
        assertThat(start).containsEntry("character", "joseph")
                .containsEntry("currentScene", 1);
        Map<String, Object> payload = (Map<String, Object>) start.get("scenePayload");
        assertThat((String) payload.get("title")).contains("파라오");
    }

    @Test
    void no_token_protected_path_unauthorized() {
        try {
            client().get().uri("/api/users/me").retrieve().body(String.class);
        } catch (HttpClientErrorException e) {
            HttpStatusCode s = e.getStatusCode();
            assertThat(s.value()).isIn(401, 403);
        }
    }
}
