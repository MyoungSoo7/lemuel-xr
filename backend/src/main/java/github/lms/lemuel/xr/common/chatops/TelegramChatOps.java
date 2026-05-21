package github.lms.lemuel.xr.common.chatops;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 운영 알람을 텔레그램 봇으로 전송 — server-monitor 와 동일 패턴.
 *
 * <p>chatops.telegram.enabled=false 면 no-op. dev 환경 기본 disabled.</p>
 */
@Component
@Slf4j
public class TelegramChatOps {

    private final TelegramChatOpsProperties props;
    private final WebClient client;

    public TelegramChatOps(TelegramChatOpsProperties props) {
        this.props = props;
        this.client = WebClient.builder().baseUrl(props.baseUrl()).build();
    }

    /** plain text alert. markdown 이 필요하면 별도. */
    public void notify(Severity severity, String title, String body) {
        if (!props.enabled() || props.botToken() == null || props.chatId() == null) {
            log.debug("Telegram chatops disabled — skipping [{}] {}", severity, title);
            return;
        }
        String text = severity.icon + " *[lemuel-xr]* " + escape(title) + "\n" + escape(body);
        try {
            client.post()
                    .uri("/bot" + props.botToken() + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "chat_id", props.chatId(),
                            "text", text,
                            "parse_mode", "MarkdownV2"
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .block();
            log.info("Telegram alert sent [{}] {}", severity, title);
        } catch (Exception e) {
            log.warn("Telegram alert 실패 [{}] {}: {}", severity, title, e.getMessage());
        }
    }

    /** MarkdownV2 의 reserved char escape. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!])", "\\\\$1");
    }

    public enum Severity {
        INFO("ℹ️"), WARN("⚠️"), ERROR("🔴"), CRITICAL("🚨");
        public final String icon;
        Severity(String icon) { this.icon = icon; }
    }

    @Configuration
    @EnableConfigurationProperties(TelegramChatOpsProperties.class)
    public static class Config {}
}
