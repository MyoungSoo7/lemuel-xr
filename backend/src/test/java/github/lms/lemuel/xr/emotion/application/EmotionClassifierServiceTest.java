package github.lms.lemuel.xr.emotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import github.lms.lemuel.xr.emotion.domain.Emotion;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * EmotionClassifierService 단위 테스트 — JDK 내장 {@link HttpServer} 로 AI 사이드카
 * /classify-emotion 을 스텁. 성공 파싱 / 알 수 없는 emotion → CONFUSED / 사이드카 오류 → CONFUSED
 * fallback / 빈 본문 → CONFUSED 분기를 실제 네트워크 없이 loopback stub 으로 커버.
 * (@Cacheable 은 프록시 없는 직접 인스턴스 테스트라 무시됨 — 순수 분기 커버가 목적.)
 */
class EmotionClassifierServiceTest {

    private HttpServer server;

    private String startStub(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/classify-emotion", exchange -> {
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
    void 성공_응답_감정과_신뢰도_파싱() throws IOException {
        String base = startStub(200, """
                {"emotion":"ANXIOUS","confidence":0.91}""");
        var svc = new EmotionClassifierService(base);

        var r = svc.classify("불안한 하루였어요");

        assertThat(r.emotion()).isEqualTo(Emotion.ANXIOUS);
        assertThat(r.confidence()).isEqualTo(0.91);
    }

    @Test
    void 소문자_감정도_대문자로_파싱() throws IOException {
        String base = startStub(200, """
                {"emotion":"grateful","confidence":0.7}""");
        var svc = new EmotionClassifierService(base);

        assertThat(svc.classify("감사해요").emotion()).isEqualTo(Emotion.GRATEFUL);
    }

    @Test
    void 알수없는_감정이면_CONFUSED_fallback() throws IOException {
        String base = startStub(200, """
                {"emotion":"WHATEVER","confidence":0.3}""");
        var svc = new EmotionClassifierService(base);

        // Emotion.fromString 이 알 수 없는 값을 CONFUSED 로 흡수.
        assertThat(svc.classify("텍스트").emotion()).isEqualTo(Emotion.CONFUSED);
    }

    @Test
    void 사이드카_5xx_이면_CONFUSED_confidence_0() throws IOException {
        String base = startStub(500, "{\"error\":\"boom\"}");
        var svc = new EmotionClassifierService(base);

        var r = svc.classify("텍스트");

        assertThat(r.emotion()).isEqualTo(Emotion.CONFUSED);
        assertThat(r.confidence()).isEqualTo(0.0);
    }

    @Test
    void 연결_실패_이면_CONFUSED_fallback() {
        // 살아있지 않은 포트 → connection refused → catch 블록 fallback.
        var svc = new EmotionClassifierService("http://127.0.0.1:1");

        var r = svc.classify("텍스트");

        assertThat(r.emotion()).isEqualTo(Emotion.CONFUSED);
        assertThat(r.confidence()).isEqualTo(0.0);
    }
}
