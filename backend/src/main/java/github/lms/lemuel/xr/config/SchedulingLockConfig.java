package github.lms.lemuel.xr.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ShedLock — @Scheduled 메서드의 분산 락 (replicas N 개 중 1 개만 실행 보장).
 *
 * <p>현재 lemuel-xr 은 replicas: 1 이지만 *ArgoCD rolling update 중 옛/새 pod 가 잠시
 * 공존하는 순간* 의 중복 발행도 차단. 미래 HA 전환 시 코드 변경 없이 바로 동작.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingLockConfig {

    @Bean
    public LockProvider shedLockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
