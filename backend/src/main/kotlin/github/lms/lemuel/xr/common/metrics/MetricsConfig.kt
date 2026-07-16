package github.lms.lemuel.xr.common.metrics

import io.micrometer.core.aop.TimedAspect
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Micrometer `@Timed` 어노테이션을 실제 측정하도록 AOP aspect 활성화.
 *
 * Spring Boot 4 는 autoconfig 에서 자동 제공하지 않으므로 명시 등록.
 */
@Configuration
class MetricsConfig {

    @Bean
    fun timedAspect(registry: MeterRegistry): TimedAspect = TimedAspect(registry)
}
