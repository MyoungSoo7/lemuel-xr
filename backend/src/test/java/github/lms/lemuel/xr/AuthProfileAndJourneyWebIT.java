package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * AuthController 프로필 PATCH(safety / ai-opt-out) + /api/users/me disclaimer 상태 +
 * JourneyController(weekly / today) 통합 테스트.
 *
 * <p>기존 IT 가 다루지 않은 auth/web(70%)·journey/web(0%) 커버.</p>
 */
class AuthProfileAndJourneyWebIT extends IntegrationTestBase {

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
                .body(Map.of("deviceFingerprint", "auth-journey-it-" + System.nanoTime(),
                        "deviceType", "quest3"))
                .retrieve().body(Map.class);
        this.token = (String) guest.get("token");
        assertThat(token).isNotBlank();
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);
    }

    // ─────────────────────────── /api/users/me disclaimer 상태 ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void me_disclaimer동의후_accepted_true() {
        Map<String, Object> me = authed().get().uri("/api/users/me").retrieve().body(Map.class);
        assertThat(me).containsEntry("userType", "guest");
        Map<String, Object> disclaimer = (Map<String, Object>) me.get("disclaimer");
        assertThat(disclaimer).containsEntry("accepted", true);
        assertThat(disclaimer.get("currentVersion")).isEqualTo("1.0");
        assertThat(me.get("aiOptOut")).isEqualTo(false);
    }

    // ─────────────────────────── PATCH /api/users/me/safety ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void patch_safety_설정_반영() {
        Map<String, Object> body = authed().patch().uri("/api/users/me/safety")
                .body(Map.of("hapticIntensity", "high", "skipIntroSilence", true,
                        "dataRetentionDays", 45))
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("hapticIntensity", "high")
                .containsEntry("skipIntroSilence", true)
                .containsEntry("dataRetentionDays", 45);
    }

    // ─────────────────────────── PATCH /api/users/me/ai-opt-out ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void patch_aiOptOut_true후_me에_반영() {
        Map<String, Object> opt = authed().patch().uri("/api/users/me/ai-opt-out")
                .body(Map.of("optOut", true))
                .retrieve().body(Map.class);
        assertThat(opt).containsEntry("optOut", true);

        Map<String, Object> me = authed().get().uri("/api/users/me").retrieve().body(Map.class);
        assertThat(me.get("aiOptOut")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void patch_aiOptOut_null_false처리() {
        Map<String, Object> opt = authed().patch().uri("/api/users/me/ai-opt-out")
                .body(Map.of())  // optOut 없음 → false.
                .retrieve().body(Map.class);
        assertThat(opt).containsEntry("optOut", false);
    }

    // ─────────────────────────── /api/journey/weekly ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void journey_weekly_신규사용자_week0_joseph() {
        Map<String, Object> body = authed().get().uri("/api/journey/weekly").retrieve().body(Map.class);
        // 실천 이력 없는 신규 게스트 → week 0.
        assertThat(body).containsEntry("weekIndex", 0).containsEntry("character", "joseph");
        assertThat((String) body.get("theme")).contains("신중함");
        assertThat((List<Integer>) body.get("values")).containsExactly(1, 2);
    }

    // ─────────────────────────── /api/journey/today ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void journey_today_신규사용자_valueId1() {
        Map<String, Object> body = authed().get().uri("/api/journey/today").retrieve().body(Map.class);
        assertThat(body).containsEntry("weekIndex", 0).containsEntry("character", "joseph")
                .containsEntry("valueId", 1);
        // 카드 시드 없으면 null, 있으면 map — 존재 여부만 확인 (NPE 없이 응답).
        assertThat(body).containsKey("card");
    }
}
