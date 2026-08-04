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
                    // Prometheus 스크레이프 대상. 인증을 걸면 ServiceMonitor 가 403 을 받는다 —
                    // prometheus-operator 의 ServiceMonitor 는 basicAuth/Bearer 만 지원해서
                    // 이 앱의 X-Internal-Token 방식을 태울 수 없다.
                    //
                    // 노출 범위를 실측으로 확인하고 여는 것이다(2026-08-05):
                    //   - Cloudflare Tunnel 의 xr.lemuel.co.kr 규칙은 192.168.219.111:30089
                    //     (frontend NodePort) 로만 간다. backend NodePort(30191)·Traefik :80 은
                    //     터널 규칙에 아예 없다. 실제로 https://xr.lemuel.co.kr/actuator/health 는
                    //     Next.js 404 페이지를 돌려준다 — 즉 backend 로 가지 않는다.
                    //   - 따라서 이 endpoint 는 홈 LAN(192.168.219.0/24) 안에서만 도달 가능하고,
                    //     그건 Prometheus 자신이 있는 신뢰 경계와 같다.
                    //
                    // 터널에 backend 를 직접 물리게 되는 날에는 이 줄을 되돌리고
                    // management.server.port 분리로 가야 한다. 그때 이 주석이 근거다.
                    .requestMatchers("/actuator/prometheus").permitAll()
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
