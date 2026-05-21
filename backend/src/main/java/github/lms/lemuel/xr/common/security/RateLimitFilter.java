package github.lms.lemuel.xr.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 토큰 버킷 — userId(or IP) + 경로 그룹별 분당 호출 제한.
 *
 * <p>application.yml security.rate-limit.* 의 값을 읽음. 단순 in-memory Caffeine
 * 카운터 (분 단위 expire). 분산 환경에서는 Redis 백엔드로 교체.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int defaultPerMin;
    private final int classifyPerMin;
    private final int llmRealtimePerMin;
    private final int ttsMissPerMin;

    // key = "<userId-or-ip>:<group>:<yyyy-MM-dd-HH-mm>" → count
    private final Cache<String, AtomicInteger> buckets = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.rate-limit.default-per-minute:60}") int defaultPerMin,
            @Value("${security.rate-limit.classify-per-minute:20}") int classifyPerMin,
            @Value("${security.rate-limit.llm-realtime-per-minute:30}") int llmRealtimePerMin,
            @Value("${security.rate-limit.tts-miss-per-minute:10}") int ttsMissPerMin) {
        this.enabled = enabled;
        this.defaultPerMin = defaultPerMin;
        this.classifyPerMin = classifyPerMin;
        this.llmRealtimePerMin = llmRealtimePerMin;
        this.ttsMissPerMin = ttsMissPerMin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(req, res);
            return;
        }
        String uri = req.getRequestURI();
        int limit = limitFor(uri);
        if (limit <= 0) {
            chain.doFilter(req, res);
            return;
        }

        String actor = req.getAttribute("xr.userId") != null
                ? req.getAttribute("xr.userId").toString()
                : req.getRemoteAddr();
        String minuteKey = String.valueOf(System.currentTimeMillis() / 60_000L);
        String key = actor + ":" + groupOf(uri) + ":" + minuteKey;

        AtomicInteger counter = buckets.get(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count > limit) {
            res.setStatus(429);  // 429 Too Many Requests — jakarta.servlet 상수 없음
            res.setContentType("application/problem+json;charset=UTF-8");
            res.setHeader("Retry-After", "60");
            res.getWriter().write("""
                {"type":"https://lemuel.co.kr/errors/E_RATE_LIMITED",\
                "title":"Too many requests","status":429,"code":"E_RATE_LIMITED"}""");
            return;
        }
        chain.doFilter(req, res);
    }

    private int limitFor(String uri) {
        if (uri.startsWith("/api/emotion/classify")) return classifyPerMin;
        if (uri.contains("/decide") && uri.startsWith("/api/game/")) return llmRealtimePerMin;
        if (uri.startsWith("/api/tts/synthesize")) return ttsMissPerMin;
        if (uri.startsWith("/api/internal/") || uri.startsWith("/actuator/")) return 0;
        return defaultPerMin;
    }

    private String groupOf(String uri) {
        if (uri.startsWith("/api/emotion/classify")) return "classify";
        if (uri.contains("/decide")) return "decide";
        if (uri.startsWith("/api/tts/synthesize")) return "tts";
        return "default";
    }
}
