package github.lms.lemuel.xr.common.chatops

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/** 부팅 시 한 번 텔레그램에 ready 알림. */
@Component
class BootAlert(
    private val chatOps: TelegramChatOps,
    private val env: Environment,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        chatOps.notify(
            TelegramChatOps.Severity.INFO,
            "Backend ready",
            "profile=" + env.activeProfiles.joinToString(",") +
                " port=" + env.getProperty("server.port", "8080"),
        )
    }
}
