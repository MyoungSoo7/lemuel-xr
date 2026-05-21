package github.lms.lemuel.xr.common.security;

import github.lms.lemuel.xr.common.jwt.JwtAuthFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import github.lms.lemuel.xr.common.jwt.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtFilter,
                                                   InternalTokenFilter internalFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
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
            )
            .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
