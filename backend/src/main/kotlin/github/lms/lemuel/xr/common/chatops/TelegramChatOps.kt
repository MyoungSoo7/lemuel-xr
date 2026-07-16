package github.lms.lemuel.xr.common.chatops

import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

/**
 * 운영 알람을 텔레그램 봇으로 전송 — server-monitor 와 동일 패턴.
 *
 * chatops.telegram.enabled=false 면 no-op. dev 환경 기본 disabled.
 */
@Component
class TelegramChatOps(private val props: TelegramChatOpsProperties) {

    private val client: WebClient = WebClient.builder().baseUrl(props.baseUrl).build()

    /** plain text alert. markdown 이 필요하면 별도. */
    fun notify(severity: Severity, title: String, body: String) {
        if (!props.enabled || props.botToken == null || props.chatId == null) {
            log.debug("Telegram chatops disabled — skipping [{}] {}", severity, title)
            return
        }
        val text = severity.icon + " *[lemuel-xr]* " + escape(title) + "\n" + escape(body)
        try {
            client.post()
                .uri("/bot" + props.botToken + "/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "chat_id" to props.chatId,
                        "text" to text,
                        "parse_mode" to "MarkdownV2",
                    ),
                )
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block()
            log.info("Telegram alert sent [{}] {}", severity, title)
        } catch (e: Exception) {
            log.warn("Telegram alert 실패 [{}] {}: {}", severity, title, e.message)
        }
    }

    enum class Severity(val icon: String) {
        INFO("ℹ️"), WARN("⚠️"), ERROR("🔴"), CRITICAL("🚨"),
    }

    @Configuration
    @EnableConfigurationProperties(TelegramChatOpsProperties::class)
    class Config

    companion object {
        private val log = LoggerFactory.getLogger(TelegramChatOps::class.java)

        /** MarkdownV2 의 reserved char escape. */
        private fun escape(s: String?): String {
            if (s == null) return ""
            return s.replace("([_*\\[\\]()~`>#+\\-=|{}.!])".toRegex(), "\\\\$1")
        }
    }
}
