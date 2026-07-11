package github.lms.lemuel.xr.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * X-Internal-Token 헤더 검증 — /api/internal/* 경로 보호.
 * Python AI/TTS 사이드카가 backend 의 admin API 를 호출할 때 사용.
 *
 * <p>MessageDigest.isEqual 으로 timing attack 방지. inter-asat 의
 * InternalServiceTokenFilter 패턴 그대로 재사용.</p>
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";
    private static final String PATH_PREFIX = "/api/internal/";

    private final byte[] expected;

    public InternalTokenFilter(@Value("${security.internal.token}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (!uri.startsWith(PATH_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }
        String provided = req.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/problem+json;charset=UTF-8");
            res.getWriter().write("""
                {"type":"https://lemuel.co.kr/errors/E_INTERNAL_TOKEN_INVALID",\
                "title":"Internal service token mismatch","status":401,\
                "code":"E_INTERNAL_TOKEN_INVALID"}""");
            return;
        }
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }
}
