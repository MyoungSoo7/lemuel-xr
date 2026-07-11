package github.lms.lemuel.xr.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * AiSidecarClient 단위 테스트 — 실제 LLM/네트워크 호출 대신 JDK 내장 {@link HttpServer} 로
 * Python AI 사이드카를 스텁한다. loopback stub 서버로 성공 파싱 / 빈본문 / 5xx / 연결실패 /
 * classify-emotion 성공·실패 분기를 커버. TtsSidecarClientTest 패턴 재사용.
 */
class AiSidecarClientTest {

    private static final String TOKEN = "test-internal-token";

    private HttpServer server;

    private String startStub(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    // ───────────────────────── /ai/generate ─────────────────────────

    @Test
    void generate_성공_응답_파싱() throws IOException {
        String base = startStub("/ai/generate", 200, """
                {"text":"묵상문 본문","provider":"anthropic","model":"claude-x",\
                "promptTokens":12,"completionTokens":34,"cached":true}""");
        var client = new AiSidecarClient(base, 30000, TOKEN);

        var r = client.generate("meditation", "joseph.s2", Map.of("k", "v"));

        assertThat(r.text()).isEqualTo("묵상문 본문");
        assertThat(r.provider()).isEqualTo("anthropic");
        assertThat(r.model()).isEqualTo("claude-x");
        assertThat(r.promptTokens()).isEqualTo(12);
        assertThat(r.completionTokens()).isEqualTo(34);
        assertThat(r.cached()).isTrue();
    }

    @Test
    void generate_cached_필드_없으면_false() throws IOException {
        String base = startStub("/ai/generate", 200, """
                {"text":"본문","provider":"anthropic","model":"m"}""");
        var client = new AiSidecarClient(base, 30000, TOKEN);

        var r = client.generate("meditation", "k", Map.of());

        assertThat(r.cached()).isFalse();
        assertThat(r.promptTokens()).isNull();
    }

    @Test
    void generate_빈_본문_이면_E_LLM_UPSTREAM_FAIL() throws IOException {
        // 200 이지만 body 없음 → bodyToMono(Map) == null → empty body 분기 (AppException 재던지기).
        String base = startStub("/ai/generate", 200, null);
        var client = new AiSidecarClient(base, 30000, TOKEN);

        assertThatThrownBy(() -> client.generate("meditation", "k", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL));
    }

    @Test
    void generate_사이드카_5xx_이면_E_LLM_UPSTREAM_FAIL() throws IOException {
        String base = startStub("/ai/generate", 500, "{\"error\":\"boom\"}");
        var client = new AiSidecarClient(base, 30000, TOKEN);

        assertThatThrownBy(() -> client.generate("meditation", "k", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL));
    }

    @Test
    void generate_연결_실패_이면_E_LLM_UPSTREAM_FAIL() {
        // 살아있지 않은 포트 → connection refused → generic Exception catch 분기.
        var client = new AiSidecarClient("http://127.0.0.1:1", 30000, TOKEN);

        assertThatThrownBy(() -> client.generate("meditation", "k", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL));
    }

    @Test
    void generate_타임아웃_초과_이면_E_LLM_UPSTREAM_FAIL() throws IOException {
        // 응답을 느리게 — 1ms timeout 으로 강제 초과 → TimeoutException → generic catch.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/generate", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = "{\"text\":\"late\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        var client = new AiSidecarClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), 1, TOKEN);

        assertThatThrownBy(() -> client.generate("meditation", "k", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL));
    }

    // ───────────────────────── /classify-emotion ─────────────────────────

    @Test
    void classifyEmotion_성공_응답_맵_반환() throws IOException {
        String base = startStub("/classify-emotion", 200, """
                {"emotion":"ANXIOUS","confidence":0.87}""");
        var client = new AiSidecarClient(base, 30000, TOKEN);

        Map<String, Object> resp = client.classifyEmotion("불안해요");

        assertThat(resp).containsEntry("emotion", "ANXIOUS");
        assertThat(resp).containsKey("confidence");
    }

    @Test
    void classifyEmotion_사이드카_오류_이면_E_LLM_UPSTREAM_FAIL() throws IOException {
        String base = startStub("/classify-emotion", 503, "{\"error\":\"down\"}");
        var client = new AiSidecarClient(base, 30000, TOKEN);

        assertThatThrownBy(() -> client.classifyEmotion("텍스트"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL));
    }
}
