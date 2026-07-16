package github.lms.lemuel.xr.emotion.adapter.out.sidecar

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * EmotionClassificationSidecarAdapter 단위 테스트 — JDK 내장 [HttpServer] 로 AI 사이드카
 * `/classify-emotion` 을 스텁. 성공 파싱 / 사이드카 오류 → null / 연결 실패 → null 분기를
 * 실제 네트워크 없이 loopback stub 으로 커버. (application 에서 adapter/out/sidecar 로 분리된
 * 감정 분류 HTTP 경로 — EmotionClassificationPort 구현.)
 */
class EmotionClassificationSidecarAdapterTest {

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
        val base = startStub(200, """{"emotion":"ANXIOUS","confidence":0.87}""")
        val adapter = EmotionClassificationSidecarAdapter(base)

        val resp = adapter.classify("불안해요")

        assertThat(resp).isNotNull()
        assertThat(resp!!.emotion).isEqualTo("ANXIOUS")
        assertThat(resp.confidence).isEqualTo(0.87)
    }

    @Test
    fun `사이드카 오류 이면 null`() {
        val base = startStub(503, """{"error":"down"}""")
        val adapter = EmotionClassificationSidecarAdapter(base)

        assertThat(adapter.classify("텍스트")).isNull()
    }

    @Test
    fun `연결 실패 이면 null`() {
        // 살아있지 않은 포트 → connection refused → catch 블록 null 반환.
        val adapter = EmotionClassificationSidecarAdapter("http://127.0.0.1:1")

        assertThat(adapter.classify("텍스트")).isNull()
    }
}
