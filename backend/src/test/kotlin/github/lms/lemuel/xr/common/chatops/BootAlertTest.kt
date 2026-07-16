package github.lms.lemuel.xr.common.chatops

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.mock.env.MockEnvironment

/**
 * BootAlert 단위 테스트 — ApplicationReadyEvent 시 profile/port 를 담아 INFO 알림을 보낸다.
 */
class BootAlertTest {

    @Test
    fun `onReady 는 profile 와 port 를 담아 INFO 알림 전송`() {
        val chatOps: TelegramChatOps = mock()
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "k8s")
        env.setProperty("server.port", "9000")

        BootAlert(chatOps, env).onReady()

        verify(chatOps).notify(
            eq(TelegramChatOps.Severity.INFO),
            eq("Backend ready"),
            contains("profile=prod,k8s"),
        )
    }

    @Test
    fun `server port 미설정이면 기본값 8080 사용`() {
        val chatOps: TelegramChatOps = mock()
        val env = MockEnvironment() // 활성 프로파일 없음, port 미설정

        BootAlert(chatOps, env).onReady()

        verify(chatOps).notify(
            eq(TelegramChatOps.Severity.INFO),
            eq("Backend ready"),
            contains("port=8080"),
        )
    }
}
