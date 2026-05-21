package github.lms.lemuel.xr.common.chatops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** chatops.telegram.* 설정. */
@ConfigurationProperties(prefix = "chatops.telegram")
public record TelegramChatOpsProperties(
        boolean enabled,
        String botToken,
        String chatId,
        String baseUrl     // 기본 https://api.telegram.org
) {
    public TelegramChatOpsProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.telegram.org";
    }
}
