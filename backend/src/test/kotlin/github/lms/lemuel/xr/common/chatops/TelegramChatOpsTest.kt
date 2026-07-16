package github.lms.lemuel.xr.common.chatops

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * TelegramChatOps 단위 테스트 — WebClient 대상 텔레그램 API 를 JDK [HttpServer] 로 스텁.
 *
 * notify() 는 실패해도 예외를 던지지 않는 fire-and-forget 이므로, 실제 HTTP hit 여부를
 * 스텁 서버 콜 카운터로 검증한다. 분기: disabled/토큰없음/chatId없음 no-op, 성공, 5xx(catch),
 * escape() reserved char.
 */
class TelegramChatOpsTest {

    private var server: HttpServer? = null
    private val hits = AtomicInteger(0)

    private fun startStub(status: Int): String {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server = srv
        srv.createContext("/") { exchange ->
            hits.incrementAndGet()
            val bytes = "{\"ok\":true}".toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        srv.start()
        return "http://127.0.0.1:" + srv.address.port
    }

    private fun chatOps(enabled: Boolean, botToken: String?, chatId: String?, baseUrl: String?): TelegramChatOps =
        TelegramChatOps(TelegramChatOpsProperties(enabled, botToken, chatId, baseUrl))

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `disabled 이면 HTTP 호출 없이 no op`() {
        val base = startStub(200)
        val ops = chatOps(false, "token", "123", base)

        ops.notify(TelegramChatOps.Severity.INFO, "제목", "본문")

        assertThat(hits.get()).isZero()
    }

    @Test
    fun `botToken null 이면 no op`() {
        val base = startStub(200)
        val ops = chatOps(true, null, "123", base)

        ops.notify(TelegramChatOps.Severity.WARN, "제목", "본문")

        assertThat(hits.get()).isZero()
    }

    @Test
    fun `chatId null 이면 no op`() {
        val base = startStub(200)
        val ops = chatOps(true, "token", null, base)

        ops.notify(TelegramChatOps.Severity.ERROR, "제목", "본문")

        assertThat(hits.get()).isZero()
    }

    @Test
    fun `활성화되면 텔레그램 API 를 호출한다`() {
        val base = startStub(200)
        val ops = chatOps(true, "bot-token", "chat-1", base)

        ops.notify(TelegramChatOps.Severity.CRITICAL, "긴급", "서버 다운")

        assertThat(hits.get()).isEqualTo(1)
    }

    @Test
    fun `상위 5xx 응답이어도 예외를 삼킨다`() {
        val base = startStub(500)
        val ops = chatOps(true, "bot-token", "chat-1", base)

        // block() 이 WebClientResponseException 을 던져도 catch 로 흡수 — 예외 전파 없어야 함.
        ops.notify(TelegramChatOps.Severity.INFO, "제목", "본문")

        assertThat(hits.get()).isEqualTo(1)
    }

    @Test
    fun `연결 실패해도 예외를 삼킨다`() {
        // 살아있지 않은 포트 → connection refused → catch(Exception) 분기.
        val ops = chatOps(true, "bot-token", "chat-1", "http://127.0.0.1:1")

        ops.notify(TelegramChatOps.Severity.INFO, "제목", "본문")
        // 예외 없이 반환되면 통과.
    }

    @Test
    fun `reserved char 이스케이프 포함 제목도 전송`() {
        // escape() 의 replaceAll 분기 커버 — 특수문자 다수 포함.
        val base = startStub(200)
        val ops = chatOps(true, "bot-token", "chat-1", base)

        ops.notify(TelegramChatOps.Severity.INFO, "a_b*c[d]e.f!", "line-one-(x)")

        assertThat(hits.get()).isEqualTo(1)
    }

    @Test
    fun `baseUrl 기본값이 적용된다`() {
        // 생성자에서 null baseUrl → 기본값 세팅.
        val props = TelegramChatOpsProperties(false, null, null, null)
        assertThat(props.baseUrl).isEqualTo("https://api.telegram.org")

        // enabled=false 라 notify 호출해도 no-op (HTTP 없음).
        val ops = TelegramChatOps(props)
        ops.notify(TelegramChatOps.Severity.INFO, "t", "b")
    }

    @Test
    fun `Severity 아이콘 노출`() {
        assertThat(TelegramChatOps.Severity.INFO.icon).isNotBlank()
        assertThat(TelegramChatOps.Severity.CRITICAL.icon).isNotBlank()
    }
}
