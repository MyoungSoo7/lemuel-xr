package github.lms.lemuel.xr.common.security

import github.lms.lemuel.xr.common.jwt.JwtAuthFilter
import github.lms.lemuel.xr.common.jwt.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtFilter: JwtAuthFilter,
        internalFilter: InternalTokenFilter,
        disclaimerGateFilter: DisclaimerGateFilter,
        disclaimerHeaderFilter: DisclaimerHeaderFilter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 — auth/scripture/topics 카탈로그/health
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/scripture/**").permitAll()
                    .requestMatchers("/api/content/topics", "/api/content/topics/**").permitAll()
                    .requestMatchers("/api/tts/voices").permitAll()
                    .requestMatchers("/api/config/**").permitAll()
                    .requestMatchers("/api/safety/crisis-resources").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    // 내부 (X-Internal-Token)
                    .requestMatchers("/api/internal/**").hasRole("INTERNAL")
                    // 그 외 모두 인증 필요
                    .anyRequest().authenticated()
            }
            .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            // DisclaimerGateFilter: JWT 검증 이후, 콘텐츠 endpoint 진입 전에 동의 검증.
            .addFilterAfter(disclaimerGateFilter, JwtAuthFilter::class.java)
            // DisclaimerHeaderFilter: 모든 응답에 X-Lemuel-* 헤더 박음 (Layer 5).
            .addFilterAfter(disclaimerHeaderFilter, DisclaimerGateFilter::class.java)
        return http.build()
    }
}
