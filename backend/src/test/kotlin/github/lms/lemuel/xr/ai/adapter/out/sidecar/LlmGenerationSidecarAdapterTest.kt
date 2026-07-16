package github.lms.lemuel.xr.ai.adapter.out.sidecar

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
 * LlmGenerationSidecarAdapter 단위 테스트 — 실제 LLM/네트워크 호출 대신 JDK 내장 [HttpServer] 로
 * Python AI 사이드카의 `/ai/generate` 를 스텁한다. loopback stub 서버로 성공 파싱 / 빈본문 /
 * 5xx / 연결실패 / 타임아웃 분기를 커버. (감정 분류는 EmotionClassificationSidecarAdapterTest 로 분리됨.)
 */
class LlmGenerationSidecarAdapterTest {

    private var server: HttpServer? = null

    private fun startStub(path: String, status: Int, body: String?): String {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server = srv
        srv.createContext(path) { exchange ->
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

    // ───────────────────────── /ai/generate ─────────────────────────

    @Test
    fun `generate 성공 응답 파싱`() {
        val base = startStub(
            "/ai/generate", 200,
            """{"text":"묵상문 본문","provider":"anthropic","model":"claude-x",""" +
                """"promptTokens":12,"completionTokens":34,"cached":true}""",
        )
        val adapter = LlmGenerationSidecarAdapter(base, 30000, TOKEN)

        val r = adapter.generate("meditation", "joseph.s2", mapOf("k" to "v"))

        assertThat(r.text).isEqualTo("묵상문 본문")
        assertThat(r.provider).isEqualTo("anthropic")
        assertThat(r.model).isEqualTo("claude-x")
        assertThat(r.promptTokens).isEqualTo(12)
        assertThat(r.completionTokens).isEqualTo(34)
        assertThat(r.cached).isTrue()
    }

    @Test
    fun `generate cached 필드 없으면 false`() {
        val base = startStub(
            "/ai/generate", 200,
            """{"text":"본문","provider":"anthropic","model":"m"}""",
        )
        val adapter = LlmGenerationSidecarAdapter(base, 30000, TOKEN)

        val r = adapter.generate("meditation", "k", emptyMap())

        assertThat(r.cached).isFalse()
        assertThat(r.promptTokens).isNull()
    }

    @Test
    fun `generate 빈 본문 이면 E_LLM_UPSTREAM_FAIL`() {
        // 200 이지만 body 없음 → bodyToMono(Map) == null → empty body 분기 (AppException 재던지기).
        val base = startStub("/ai/generate", 200, null)
        val adapter = LlmGenerationSidecarAdapter(base, 30000, TOKEN)

        assertThatThrownBy { adapter.generate("meditation", "k", emptyMap()) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL)
            })
    }

    @Test
    fun `generate 사이드카 5xx 이면 E_LLM_UPSTREAM_FAIL`() {
        val base = startStub("/ai/generate", 500, """{"error":"boom"}""")
        val adapter = LlmGenerationSidecarAdapter(base, 30000, TOKEN)

        assertThatThrownBy { adapter.generate("meditation", "k", emptyMap()) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL)
            })
    }

    @Test
    fun `generate 연결 실패 이면 E_LLM_UPSTREAM_FAIL`() {
        // 살아있지 않은 포트 → connection refused → generic Exception catch 분기.
        val adapter = LlmGenerationSidecarAdapter("http://127.0.0.1:1", 30000, TOKEN)

        assertThatThrownBy { adapter.generate("meditation", "k", emptyMap()) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL)
            })
    }

    @Test
    fun `generate 타임아웃 초과 이면 E_LLM_UPSTREAM_FAIL`() {
        // 응답을 느리게 — 1ms timeout 으로 강제 초과 → TimeoutException → generic catch.
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server = srv
        srv.createContext("/ai/generate") { exchange ->
            try {
                Thread.sleep(500)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val bytes = """{"text":"late"}""".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        srv.start()
        val adapter = LlmGenerationSidecarAdapter(
            "http://127.0.0.1:" + srv.address.port, 1, TOKEN,
        )

        assertThatThrownBy { adapter.generate("meditation", "k", emptyMap()) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e ->
                assertThat((e as AppException).code).isEqualTo(ErrorCode.E_LLM_UPSTREAM_FAIL)
            })
    }

    companion object {
        private const val TOKEN = "test-internal-token"
    }
}
