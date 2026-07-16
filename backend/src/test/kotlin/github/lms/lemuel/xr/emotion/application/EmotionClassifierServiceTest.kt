package github.lms.lemuel.xr.emotion.application

import com.sun.net.httpserver.HttpServer
import github.lms.lemuel.xr.emotion.adapter.out.sidecar.EmotionClassificationSidecarAdapter
import github.lms.lemuel.xr.emotion.domain.Emotion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * EmotionClassifierService 단위 테스트 — JDK 내장 [HttpServer] 로 AI 사이드카
 * /classify-emotion 을 스텁. 성공 파싱 / 알 수 없는 emotion → CONFUSED / 사이드카 오류 → CONFUSED
 * fallback / 빈 본문 → CONFUSED 분기를 실제 네트워크 없이 loopback stub 으로 커버.
 * HTTP 는 [EmotionClassificationSidecarAdapter](EmotionClassificationPort 구현) 에 위임되므로
 * 서비스는 포트 구현을 주입받아 조립한다.
 * (@Cacheable 은 프록시 없는 직접 인스턴스 테스트라 무시됨 — 순수 분기 커버가 목적.)
 */
class EmotionClassifierServiceTest {

    private var server: HttpServer? = null

    private fun startStub(status: Int, body: String?): String {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server = srv
        srv.createContext("/classify-emotion") { exchange ->
            val bytes = body?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1L else bytes.size.toLong())
            if (bytes.isNotEmpty()) {
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.close()
            }
        }
        srv.start()
        return "http://127.0.0.1:" + srv.address.port
    }

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `성공 응답 감정과 신뢰도 파싱`() {
        val base = startStub(200, """{"emotion":"ANXIOUS","confidence":0.91}""")
        val svc = EmotionClassifierService(EmotionClassificationSidecarAdapter(base))

        val r = svc.classify("불안한 하루였어요")

        assertThat(r.emotion).isEqualTo(Emotion.ANXIOUS)
        assertThat(r.confidence).isEqualTo(0.91)
    }

    @Test
    fun `소문자 감정도 대문자로 파싱`() {
        val base = startStub(200, """{"emotion":"grateful","confidence":0.7}""")
        val svc = EmotionClassifierService(EmotionClassificationSidecarAdapter(base))

        assertThat(svc.classify("감사해요").emotion).isEqualTo(Emotion.GRATEFUL)
    }

    @Test
    fun `알수없는 감정이면 CONFUSED fallback`() {
        val base = startStub(200, """{"emotion":"WHATEVER","confidence":0.3}""")
        val svc = EmotionClassifierService(EmotionClassificationSidecarAdapter(base))

        // Emotion.fromString 이 알 수 없는 값을 CONFUSED 로 흡수.
        assertThat(svc.classify("텍스트").emotion).isEqualTo(Emotion.CONFUSED)
    }

    @Test
    fun `사이드카 5xx 이면 CONFUSED confidence 0`() {
        val base = startStub(500, """{"error":"boom"}""")
        val svc = EmotionClassifierService(EmotionClassificationSidecarAdapter(base))

        val r = svc.classify("텍스트")

        assertThat(r.emotion).isEqualTo(Emotion.CONFUSED)
        assertThat(r.confidence).isEqualTo(0.0)
    }

    @Test
    fun `연결 실패 이면 CONFUSED fallback`() {
        // 살아있지 않은 포트 → connection refused → catch 블록 fallback.
        val svc = EmotionClassifierService(EmotionClassificationSidecarAdapter("http://127.0.0.1:1"))

        val r = svc.classify("텍스트")

        assertThat(r.emotion).isEqualTo(Emotion.CONFUSED)
        assertThat(r.confidence).isEqualTo(0.0)
    }
}
