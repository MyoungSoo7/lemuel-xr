package github.lms.lemuel.xr.common.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot 4 는 Jackson 3 (`tools.jackson.databind.ObjectMapper`) 를 기본 ObjectMapper 로 제공.
 * 일부 레거시 컴포넌트(AssetManifestSeeder 등) 는 Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`)
 * 를 사용 — 별도 Jackson 2 bean 을 명시 제공해 호환성 유지.
 */
@Configuration
class JacksonCompatConfig {

    @Bean
    fun legacyJacksonObjectMapper(): ObjectMapper = ObjectMapper()
}
