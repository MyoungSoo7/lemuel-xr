package github.lms.lemuel.xr.common.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 모든 도메인의 JPA Repository 활성화. */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "github.lms.lemuel.xr")
public class JpaConfig {
}
