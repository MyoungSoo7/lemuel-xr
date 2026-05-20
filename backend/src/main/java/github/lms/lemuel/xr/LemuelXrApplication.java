package github.lms.lemuel.xr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Lemuel XR — 기독교 묵상 + 요셉 인물 게임 XR 플랫폼.
 *
 * <p>헥사고날 아키텍처: adapter/in/web ↔ application/service ↔ adapter/out/persistence.
 * settlement 모듈과 동일 패턴.</p>
 */
@SpringBootApplication
@EnableCaching
public class LemuelXrApplication {
    public static void main(String[] args) {
        SpringApplication.run(LemuelXrApplication.class, args);
    }
}
