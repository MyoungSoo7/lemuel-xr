package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * game/adapter/in/web/GameController 통합 테스트.
 *
 * <p>4 인물(joseph/moses/david/jesus) + job 시나리오 로딩, /start → /decide → /complete
 * 전체 흐름과 에러 분기(잘못된 character/세션/완료된 세션)를 실 서버로 검증.
 * ContentWebIT 의 게스트 발급 → disclaimer 동의 인증 패턴을 재사용.
 */
class GameWebIT extends IntegrationTestBase {

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
        Map<String, Object> guest = rest.post().uri("/api/auth/guest")
                .body(Map.of("deviceFingerprint", "game-web-it-" + System.nanoTime(),
                        "deviceType", "quest3"))
                .retrieve().body(Map.class);
        this.token = (String) guest.get("token");
        assertThat(token).isNotBlank();
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> start(String character, String mode) {
        return authed().post().uri("/api/game/{c}/start", character)
                .body(mode == null ? Map.of() : Map.of("mode", mode))
                .retrieve().body(Map.class);
    }

    // ───────────────────────── 4 인물 + job 시나리오 로드 확인 ─────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void 다섯_시나리오_start_로드() {
        for (String c : List.of("joseph", "moses", "david", "jesus", "job")) {
            Map<String, Object> body = start(c, "emotional");
            assertThat(body).as("character %s", c).containsEntry("character", c);
            assertThat((Integer) body.get("currentScene")).isEqualTo(1);
            assertThat((Integer) body.get("totalScenes")).isGreaterThan(0);
            assertThat(body.get("sessionId")).isNotNull();
            Map<String, Object> payload = (Map<String, Object>) body.get("scenePayload");
            assertThat(payload).containsEntry("sceneId", 1);
        }
    }

    // ───────────────────────── start → decide → complete 전체 흐름 ─────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void joseph_start_decide_complete_전체흐름() {
        Map<String, Object> started = start("joseph", "emotional");
        UUID sid = UUID.fromString((String) started.get("sessionId"));

        // Scene 2 저장 결정 → monologue 응답
        Map<String, Object> decide = authed().post().uri("/api/game/joseph/{sid}/decide", sid)
                .body(Map.of("sceneId", 2, "decision", Map.of("value", "save_33")))
                .retrieve().body(Map.class);
        assertThat(decide).containsEntry("sessionId", sid.toString())
                .containsEntry("previousScene", 2)
                .containsEntry("currentScene", 3);
        assertThat((String) decide.get("responseText")).contains("요셉");

        // 완료 → valuePrompt
        Map<String, Object> complete = authed().post().uri("/api/game/joseph/{sid}/complete", sid)
                .body(Map.of("finalOutcome", "immigrant_first", "closingMessage", "수고했어요"))
                .retrieve().body(Map.class);
        assertThat(complete).containsEntry("sessionId", sid.toString());
        assertThat(complete.get("completedAt")).isNotNull();
        assertThat((Integer) complete.get("durationSeconds")).isGreaterThanOrEqualTo(0);
        Map<String, Object> vp = (Map<String, Object>) complete.get("valuePrompt");
        assertThat(vp).isNotNull();
        assertThat((List<?>) vp.get("suggestedValueIds")).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void start_client_capabilities_경로() {
        // req.client() 비-null 경로 커버 — deviceType/capabilities 를 body 에서 직접.
        Map<String, Object> body = authed().post().uri("/api/game/moses/start")
                .body(Map.of("mode", "rational",
                        "client", Map.of("deviceType", "quest2",
                                "capabilities", Map.of("handTracking", true))))
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("character", "moses").containsEntry("appliedMode", "rational");
    }

    // ───────────────────────────────── 에러 분기 ─────────────────────────────────

    @Test
    void start_잘못된_character_404() {
        try {
            authed().post().uri("/api/game/simeon/start").body(Map.of("mode", "emotional"))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
            assertThat(e.getResponseBodyAsString()).contains("E_CHARACTER_UNKNOWN");
        }
    }

    @Test
    void decide_없는_세션_404() {
        UUID ghost = UUID.randomUUID();
        try {
            authed().post().uri("/api/game/joseph/{sid}/decide", ghost)
                    .body(Map.of("sceneId", 2, "decision", Map.of("value", "save_33")))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
            assertThat(e.getResponseBodyAsString()).contains("E_SESSION_NOT_FOUND");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void decide_character_불일치_404() {
        // joseph 세션을 moses 로 decide → E_CHARACTER_UNKNOWN
        Map<String, Object> started = start("joseph", "emotional");
        UUID sid = UUID.fromString((String) started.get("sessionId"));
        try {
            authed().post().uri("/api/game/moses/{sid}/decide", sid)
                    .body(Map.of("sceneId", 2, "decision", Map.of("value", "save_33")))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void complete_두번_두번째는_409() {
        Map<String, Object> started = start("david", "emotional");
        UUID sid = UUID.fromString((String) started.get("sessionId"));
        authed().post().uri("/api/game/david/{sid}/complete", sid)
                .body(Map.of("finalOutcome", "out")).retrieve().body(Map.class);
        try {
            authed().post().uri("/api/game/david/{sid}/complete", sid)
                    .body(Map.of("finalOutcome", "out")).retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 409");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(409);
            assertThat(e.getResponseBodyAsString()).contains("E_SESSION_INVALID");
        }
    }

    @Test
    void 미인증_start_거부() {
        try {
            client().post().uri("/api/game/joseph/start").body(Map.of("mode", "emotional"))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected auth failure");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403, 451);
        }
    }
}
