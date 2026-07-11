package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.ai.application.AiSidecarClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * ai/adapter/in/web/InternalLlmController 통합 테스트.
 *
 * <p>/api/internal/llm/generate 는 X-Internal-Token 필요 (InternalTokenFilter, ROLE_INTERNAL).
 * 외부 AI 사이드카({@link AiSidecarClient}) 는 {@link MockitoBean} 으로 목킹 — 실제 LLM 호출 없음.
 * cache miss → 사이드카 호출(cached=false) → 동일 요청 캐시 히트(cached=true) → 토큰 누락 401 커버.
 * ai.generation.enabled 는 기본 true 라 miss 시 사이드카를 실제로 호출한다.</p>
 */
class InternalLlmWebIT extends IntegrationTestBase {

    // application.yml 의 security.internal.token 기본값.
    private static final String INTERNAL_TOKEN = "dev-internal-svc-token-rotate-in-prod";

    @LocalServerPort
    int port;

    @MockitoBean
    AiSidecarClient sidecar;

    private RestClient internal() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-Internal-Token", INTERNAL_TOKEN)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_최초는_사이드카_호출_cached_false_그리고_재요청은_cached_true() {
        when(sidecar.generate(any(), any(), any()))
                .thenReturn(new AiSidecarClient.GenerateResult(
                        "생성된 묵상 본문", "anthropic", "claude-x", 11, 22, false));

        // 유니크 promptKey → 이 테스트만의 캐시 키
        String promptKey = "internal.llm.it." + System.nanoTime();
        Map<String, Object> body = Map.of(
                "purpose", "meditation",
                "promptKey", promptKey,
                "variables", Map.of("k", "v"));

        Map<String, Object> first = internal().post().uri("/api/internal/llm/generate")
                .body(body).retrieve().body(Map.class);
        assertThat(first).containsEntry("text", "생성된 묵상 본문")
                .containsEntry("cached", false)
                .containsEntry("provider", "anthropic")
                .containsEntry("aiGenerated", true)
                .containsEntry("aiLabel", "AI 보조 — 본문은 성경 참조");

        // 동일 입력 → DB 캐시 히트, 사이드카 재호출 없이 cached=true.
        Map<String, Object> second = internal().post().uri("/api/internal/llm/generate")
                .body(body).retrieve().body(Map.class);
        assertThat(second).containsEntry("text", "생성된 묵상 본문")
                .containsEntry("cached", true);
    }

    @Test
    void generate_토큰_없으면_401() {
        RestClient noToken = RestClient.create("http://localhost:" + port);
        try {
            noToken.post().uri("/api/internal/llm/generate")
                    .body(Map.of("purpose", "meditation", "promptKey", "k", "variables", Map.of()))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 401");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(401);
            assertThat(e.getResponseBodyAsString()).contains("E_INTERNAL_TOKEN_INVALID");
        }
    }

    @Test
    void generate_잘못된_토큰이면_401() {
        RestClient badToken = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-Internal-Token", "wrong-token")
                .build();
        try {
            badToken.post().uri("/api/internal/llm/generate")
                    .body(Map.of("purpose", "meditation", "promptKey", "k", "variables", Map.of()))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 401");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(401);
        }
    }
}
