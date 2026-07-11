package github.lms.lemuel.xr.common.chatops;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 부팅 시 한 번 텔레그램에 ready 알림. */
@Component
@RequiredArgsConstructor
public class BootAlert {

    private final TelegramChatOps chatOps;
    private final Environment env;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        chatOps.notify(
                TelegramChatOps.Severity.INFO,
                "Backend ready",
                "profile=" + String.join(",", env.getActiveProfiles())
                        + " port=" + env.getProperty("server.port", "8080")
        );
    }
}
