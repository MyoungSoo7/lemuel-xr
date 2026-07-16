package github.lms.lemuel.xr.common.chatops

import org.springframework.boot.context.properties.ConfigurationProperties

/** chatops.telegram.* 설정. */
@ConfigurationProperties(prefix = "chatops.telegram")
class TelegramChatOpsProperties(
    val enabled: Boolean,
    val botToken: String?,
    val chatId: String?,
    baseUrl: String?,     // 기본 https://api.telegram.org
) {
    val baseUrl: String = if (baseUrl.isNullOrBlank()) "https://api.telegram.org" else baseUrl
}
