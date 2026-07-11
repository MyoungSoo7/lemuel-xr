package github.lms.lemuel.xr.tts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * TtsSidecarClient 단위 테스트 — 실제 오디오 엔진 대신 JDK 내장 {@link HttpServer} 로 사이드카를
 * 스텁한다. 네트워크 실제 호출 없이 loopback 상의 임시 stub 서버로 성공·빈본문·오류 분기를 커버.
 */
class TtsSidecarClientTest {

    private HttpServer server;

    private String startStub(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/synthesize", exchange -> {
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

    @Test
    void 성공_응답_파싱() throws IOException {
        String base = startStub(200, """
                {"audioUrl":"https://r2/audio/x.wav","durationMs":1234,"engine":"xtts-v2"}""");
        var client = new TtsSidecarClient(base);

        var r = client.synthesize("안녕하세요", "narrator-male-low", 1.0);

        assertThat(r.audioUrl()).isEqualTo("https://r2/audio/x.wav");
        assertThat(r.durationMs()).isEqualTo(1234);
        assertThat(r.engine()).isEqualTo("xtts-v2");
    }

    @Test
    void voiceId_와_rate_null_이면_기본값으로_요청() throws IOException {
        // 기본값(narrator-male-low, 1.0)으로 body 를 채우는 분기 커버.
        String base = startStub(200, """
                {"audioUrl":"https://r2/audio/y.wav","durationMs":500,"engine":"xtts-v2"}""");
        var client = new TtsSidecarClient(base);

        var r = client.synthesize("텍스트", null, null);

        assertThat(r.audioUrl()).isEqualTo("https://r2/audio/y.wav");
    }

    @Test
    void 빈_본문_이면_E_TTS_UPSTREAM_FAIL() throws IOException {
        // 200 이지만 body 없음 → bodyToMono(Map) == null → empty body 분기.
        String base = startStub(200, null);
        var client = new TtsSidecarClient(base);

        assertThatThrownBy(() -> client.synthesize("안녕", "v", 1.0))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_TTS_UPSTREAM_FAIL));
    }

    @Test
    void 사이드카_5xx_이면_E_TTS_UPSTREAM_FAIL() throws IOException {
        String base = startStub(500, "{\"error\":\"boom\"}");
        var client = new TtsSidecarClient(base);

        assertThatThrownBy(() -> client.synthesize("안녕", "v", 1.0))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_TTS_UPSTREAM_FAIL));
    }

    @Test
    void 연결_실패_이면_E_TTS_UPSTREAM_FAIL() {
        // 살아있지 않은 포트 → connection refused → generic Exception catch 분기.
        var client = new TtsSidecarClient("http://127.0.0.1:1");

        assertThatThrownBy(() -> client.synthesize("안녕", "v", 1.0))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode())
                        .isEqualTo(ErrorCode.E_TTS_UPSTREAM_FAIL));
    }
}
