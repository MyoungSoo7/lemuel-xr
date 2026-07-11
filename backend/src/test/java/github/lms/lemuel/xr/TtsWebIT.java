package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.tts.application.TtsSidecarClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * tts/adapter/in/web/TtsController 통합 테스트.
 *
 * <p>외부 TTS 사이드카({@link TtsSidecarClient}) 는 {@link MockitoBean} 으로 목킹 — 실제 오디오
 * 생성/네트워크 호출 없음. 성공(cached=false) → 동일 요청 캐시 히트(cached=true) → 사이드카
 * 오류 전파(502) 분기를 실 서버로 커버. /voices 는 공개, /synthesize 는 disclaimer 게이트 대상.
 */
class TtsWebIT extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @MockitoBean
    TtsSidecarClient sidecar;

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
                .body(Map.of("deviceFingerprint", "tts-web-it-" + System.nanoTime(),
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
    void voices_공개_카탈로그() {
        Map<String, Object> body = client().get().uri("/api/tts/voices")
                .retrieve().body(Map.class);
        List<Map<String, Object>> voices = (List<Map<String, Object>>) body.get("voices");
        assertThat(voices).hasSize(3);
        assertThat(voices.toString()).contains("narrator-male-low").contains("goliath-bass");
    }

    @Test
    @SuppressWarnings("unchecked")
    void synthesize_최초는_사이드카_호출_cached_false_그리고_재요청은_cached_true() {
        when(sidecar.synthesize(any(), any(), any()))
                .thenReturn(new TtsSidecarClient.SynthesisResult(
                        "https://r2/audio/mock.wav", 1800, "xtts-v2"));

        String text = "테스트 합성 " + System.nanoTime(); // 유니크 → 이 테스트만의 캐시 키
        Map<String, Object> first = authed().post().uri("/api/tts/synthesize")
                .body(Map.of("text", text, "voiceId", "narrator-male-low", "speakingRate", 1.0))
                .retrieve().body(Map.class);
        assertThat(first).containsEntry("audioUrl", "https://r2/audio/mock.wav")
                .containsEntry("durationMs", 1800)
                .containsEntry("cached", false);

        // 동일 입력 → DB 캐시 히트, 사이드카 재호출 없이 cached=true.
        Map<String, Object> second = authed().post().uri("/api/tts/synthesize")
                .body(Map.of("text", text, "voiceId", "narrator-male-low", "speakingRate", 1.0))
                .retrieve().body(Map.class);
        assertThat(second).containsEntry("audioUrl", "https://r2/audio/mock.wav")
                .containsEntry("cached", true);
    }

    @Test
    void synthesize_사이드카_오류면_502() {
        when(sidecar.synthesize(any(), any(), any()))
                .thenThrow(new github.lms.lemuel.xr.common.AppException(
                        github.lms.lemuel.xr.common.ErrorCode.E_TTS_UPSTREAM_FAIL, "boom"));

        try {
            authed().post().uri("/api/tts/synthesize")
                    .body(Map.of("text", "실패유도 " + System.nanoTime()))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 502");
        } catch (HttpServerErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(502);
            assertThat(e.getResponseBodyAsString()).contains("E_TTS_UPSTREAM_FAIL");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void synthesize_voiceId_rate_생략도_기본값으로_합성() {
        when(sidecar.synthesize(any(), any(), any()))
                .thenReturn(new TtsSidecarClient.SynthesisResult(
                        "https://r2/audio/def.wav", 900, "xtts-v2"));

        Map<String, Object> body = authed().post().uri("/api/tts/synthesize")
                .body(Map.of("text", "기본값 합성 " + System.nanoTime()))
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("audioUrl", "https://r2/audio/def.wav")
                .containsEntry("cached", false);
    }

    @Test
    void synthesize_인증없으면_401_또는_451() {
        try {
            client().post().uri("/api/tts/synthesize")
                    .body(Map.of("text", "hi"))
                    .retrieve().body(String.class);
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403, 451);
        }
    }
}
