package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * 여러 저커버 컨트롤러 통합 테스트 — asset(/api/config) · scripture(range/search) ·
 * analytics(/api/analytics/event) · safety(game session exit) · recovery(/api/users/me/recovery).
 *
 * <p>ContentWebIT/GameWebIT 의 게스트 발급 → disclaimer 동의 패턴 재사용.</p>
 */
class MiscEndpointsWebIT extends IntegrationTestBase {

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
                .body(Map.of("deviceFingerprint", "misc-web-it-" + System.nanoTime(),
                        "deviceType", "quest3"))
                .retrieve().body(Map.class);
        this.token = (String) guest.get("token");
        assertThat(token).isNotBlank();
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);
    }

    // ─────────────────────────── asset — /api/config/input-mapping ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void inputMapping_quest3_controller_grip() {
        Map<String, Object> body = client().get().uri("/api/config/input-mapping?device=Quest3")
                .retrieve().body(Map.class);
        Map<String, Object> grab = (Map<String, Object>) body.get("GRAB");
        assertThat(grab).containsEntry("source", "controller").containsEntry("binding", "grip");
        assertThat(grab).containsKey("fallback");
        assertThat((Map<String, Object>) body.get("POINT_AT")).containsEntry("binding", "raycast");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputMapping_visionpro_hand_pinch() {
        Map<String, Object> body = client().get().uri("/api/config/input-mapping?device=visionpro")
                .retrieve().body(Map.class);
        assertThat((Map<String, Object>) body.get("GRAB")).containsEntry("source", "hand");
        assertThat((Map<String, Object>) body.get("POINT_AT")).containsEntry("source", "eye+hand");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputMapping_galaxyxr_eye_dwell() {
        Map<String, Object> body = client().get().uri("/api/config/input-mapping?device=galaxyxr")
                .retrieve().body(Map.class);
        assertThat((Map<String, Object>) body.get("GAZE_DURATION")).containsEntry("source", "eye");
    }

    @Test
    void inputMapping_미지디바이스_400() {
        try {
            client().get().uri("/api/config/input-mapping?device=hololens")
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ─────────────────────────── asset — /api/config/asset-manifest ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void assetManifest_joseph_web_scene1_seed() {
        // AssetManifestSeeder 가 classpath manifests/joseph/web/scene1-v1.0.0.json 을 시드.
        Map<String, Object> body = client().get()
                .uri("/api/config/asset-manifest?mission=joseph&device=web&scene=1")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("missionId", "joseph").containsEntry("deviceType", "web");
        assertThat(body.get("version")).isEqualTo("1.0.0");
        assertThat(body.get("manifest")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void assetManifest_galaxyxr_scene지정() {
        Map<String, Object> body = client().get()
                .uri("/api/config/asset-manifest?mission=joseph&device=galaxyxr&scene=2")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("missionId", "joseph").containsEntry("deviceType", "galaxyxr");
        assertThat(body.get("sceneNumber")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assetManifest_scene미지정_다중매칭도_200_한건반환() {
        // 회귀: scene 파라미터 없으면 여러 씬 manifest 가 매칭돼 이전엔 NonUniqueResultException→500.
        // Limit.of(1) 로 version 최신 1건만 취해 200 반환해야 한다.
        Map<String, Object> body = client().get()
                .uri("/api/config/asset-manifest?mission=joseph&device=web")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("missionId", "joseph").containsEntry("deviceType", "web");
        assertThat(body.get("manifest")).isNotNull();
    }

    @Test
    void assetManifest_미지미션_400() {
        try {
            client().get().uri("/api/config/asset-manifest?mission=nobody&device=web")
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 4xx");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ─────────────────────────── scripture — range / search ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void scripture_range_창세기41_25_53() {
        Map<String, Object> body = client().get()
                .uri("/api/scripture/range?book=gen&chapter=41&from=25&to=53")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("book", "gen").containsEntry("chapter", 41);
        List<?> passages = (List<?>) body.get("passages");
        assertThat(passages).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void scripture_search_키워드_하나님() {
        Map<String, Object> body = client().post().uri("/api/scripture/search")
                .body(Map.of("query", "하나님", "limit", 3))
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("query", "하나님");
        List<?> matches = (List<?>) body.get("matches");
        assertThat(matches).isNotEmpty();
        assertThat(matches.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void scripture_search_limit_null_기본5() {
        Map<String, Object> body = client().post().uri("/api/scripture/search")
                .body(Map.of("query", "요셉"))
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("query", "요셉");
    }

    @Test
    void scripture_byRef_미존재_404() {
        try {
            client().get().uri("/api/scripture/gen-99:99").retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }

    // ─────────────────────────── analytics — /api/analytics/event ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void analytics_event_배치_수락() {
        Map<String, Object> body = authed().post().uri("/api/analytics/event")
                .body(Map.of("sessionId", java.util.UUID.randomUUID().toString(),
                        "events", List.of(Map.of("t", "gaze", "ms", 1200),
                                Map.of("t", "grab", "ms", 400))))
                .retrieve().body(Map.class);
        assertThat(((Number) body.get("accepted")).intValue()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void analytics_event_빈이벤트_0수락() {
        Map<String, Object> body = authed().post().uri("/api/analytics/event")
                .body(Map.of("sessionId", java.util.UUID.randomUUID().toString()))
                .retrieve().body(Map.class);
        assertThat(((Number) body.get("accepted")).intValue()).isZero();
    }

    @Test
    void analytics_event_미인증_401() {
        try {
            client().post().uri("/api/analytics/event")
                    .body(Map.of("events", List.of()))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 4xx");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403, 451);
        }
    }

    // ─────────────────────────── safety — game session emergency exit ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void safety_gameSession_exit_softLanding() {
        // 1) 게임 세션 시작 → sessionId 확보.
        Map<String, Object> start = authed().post().uri("/api/game/joseph/start")
                .body(Map.of("mode", "emotional"))
                .retrieve().body(Map.class);
        String sid = (String) start.get("sessionId");
        assertThat(sid).isNotBlank();

        // 2) emergency exit.
        Map<String, Object> exit = authed().post().uri("/api/game/sessions/{sid}/exit", sid)
                .body(Map.of("reason", "overwhelmed", "atSceneId", 1))
                .retrieve().body(Map.class);
        assertThat(exit).containsEntry("sessionId", sid);
        assertThat((String) exit.get("gentleMessage")).contains("시편 23편");
        assertThat(exit.get("exitedAt")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void safety_gameSession_exit_reason없이_기본태그() {
        Map<String, Object> start = authed().post().uri("/api/game/moses/start")
                .body(Map.of("mode", "emotional"))
                .retrieve().body(Map.class);
        String sid = (String) start.get("sessionId");

        Map<String, Object> exit = authed().post().uri("/api/game/sessions/{sid}/exit", sid)
                .body(Map.of())
                .retrieve().body(Map.class);
        assertThat(exit).containsEntry("sessionId", sid);
    }

    // ─────────────────────────── recovery — /api/users/me/recovery ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void recovery_최근30일_빈결과() {
        // 시드된 metric 없어도 200 + 빈 리스트.
        Map<String, Object> body = authed().get().uri("/api/users/me/recovery")
                .retrieve().body(Map.class);
        assertThat(body.get("items")).isNotNull();
        assertThat((List<?>) body.get("items")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void recovery_days파라미터_지정() {
        Map<String, Object> body = authed().get().uri("/api/users/me/recovery?days=7")
                .retrieve().body(Map.class);
        assertThat((List<?>) body.get("items")).isNotNull();
    }

    @Test
    void recovery_미인증_401() {
        try {
            client().get().uri("/api/users/me/recovery").retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 4xx");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403, 451);
        }
    }
}
