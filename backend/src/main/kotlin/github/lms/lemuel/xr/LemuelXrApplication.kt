package github.lms.lemuel.xr

import java.nio.charset.StandardCharsets
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Lemuel XR — 기독교 묵상 + 4 인물 서사 게임 XR 플랫폼.
 *
 * 헥사고날 아키텍처: adapter/in/web ↔ application/service ↔ adapter/out/persistence.
 * 13 bounded contexts (auth/emotion/recovery/content/game/scripture/safety/theology/ai/tts/asset/outbox/analytics).
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
class LemuelXrApplication

fun main(args: Array<String>) {
    // UTF-8 런타임 강제 — Spring Boot 4 + JDK 25 default 는 OS locale 따라가는데
    // 한국어 본문/묵상문 모두 UTF-8 로 통일.
    System.setProperty("file.encoding", StandardCharsets.UTF_8.name())
    System.setProperty("sun.jnu.encoding", StandardCharsets.UTF_8.name())
    runApplication<LemuelXrApplication>(*args)
}
