package github.lms.lemuel.xr.tts.adapter.out.sidecar

import com.sun.net.httpserver.HttpServer
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * TtsSynthesisSidecarAdapter 단위 테스트 — 실제 오디오 엔진 대신 JDK 내장 [HttpServer] 로
 * 사이드카를 스텁한다. 네트워크 실제 호출 없이 loopback 상의 임시 stub 서버로 성공·빈본문·오류 분기를 커버.
 */
class TtsSynthesisSidecarAdapterTest {

    private var server: HttpServer? = null

    private fun startStub(status: Int, body: String?): String {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server = srv
        srv.createContext("/synthesize") { exchange ->
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
    fun `성공 응답 파싱`() {
        val base = startStub(
            200,
            """{"audioUrl":"https://r2/audio/x.wav","durationMs":1234,"engine":"xtts-v2"}""",
        )
        val client = TtsSynthesisSidecarAdapter(base)

        val r = client.synthesize("안녕하세요", "narrator-male-low", 1.0)

        assertThat(r.audioUrl).isEqualTo("https://r2/audio/x.wav")
        assertThat(r.durationMs).isEqualTo(1234)
        assertThat(r.engine).isEqualTo("xtts-v2")
    }

    @Test
    fun `voiceId 와 rate null 이면 기본값으로 요청`() {
        // 기본값(narrator-male-low, 1.0)으로 body 를 채우는 분기 커버.
        val base = startStub(
            200,
            """{"audioUrl":"https://r2/audio/y.wav","durationMs":500,"engine":"xtts-v2"}""",
        )
        val client = TtsSynthesisSidecarAdapter(base)

        val r = client.synthesize("텍스트", null, null)

        assertThat(r.audioUrl).isEqualTo("https://r2/audio/y.wav")
    }

    @Test
    fun `빈 본문 이면 E_TTS_UPSTREAM_FAIL`() {
        // 200 이지만 body 없음 → bodyToMono(Map) == null → empty body 분기.
        val base = startStub(200, null)
        val client = TtsSynthesisSidecarAdapter(base)

        assertThatThrownBy { client.synthesize("안녕", "v", 1.0) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_TTS_UPSTREAM_FAIL)
            })
    }

    @Test
    fun `사이드카 5xx 이면 E_TTS_UPSTREAM_FAIL`() {
        val base = startStub(500, """{"error":"boom"}""")
        val client = TtsSynthesisSidecarAdapter(base)

        assertThatThrownBy { client.synthesize("안녕", "v", 1.0) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_TTS_UPSTREAM_FAIL)
            })
    }

    @Test
    fun `연결 실패 이면 E_TTS_UPSTREAM_FAIL`() {
        // 살아있지 않은 포트 → connection refused → generic Exception catch 분기.
        val client = TtsSynthesisSidecarAdapter("http://127.0.0.1:1")

        assertThatThrownBy { client.synthesize("안녕", "v", 1.0) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_TTS_UPSTREAM_FAIL)
            })
    }
}
