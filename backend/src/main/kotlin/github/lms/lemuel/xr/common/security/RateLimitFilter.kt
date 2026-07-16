package github.lms.lemuel.xr.common.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 토큰 버킷 — userId(or IP) + 경로 그룹별 분당 호출 제한.
 *
 * application.yml security.rate-limit.* 의 값을 읽음. 단순 in-memory Caffeine
 * 카운터 (분 단위 expire). 분산 환경에서는 Redis 백엔드로 교체.
 */
@Component
class RateLimitFilter(
    @param:Value("\${security.rate-limit.enabled:true}") private val enabled: Boolean,
    @param:Value("\${security.rate-limit.default-per-minute:60}") private val defaultPerMin: Int,
    @param:Value("\${security.rate-limit.classify-per-minute:20}") private val classifyPerMin: Int,
    @param:Value("\${security.rate-limit.llm-realtime-per-minute:30}") private val llmRealtimePerMin: Int,
    @param:Value("\${security.rate-limit.tts-miss-per-minute:10}") private val ttsMissPerMin: Int,
) : OncePerRequestFilter() {

    // key = "<userId-or-ip>:<group>:<yyyy-MM-dd-HH-mm>" → count
    private val buckets: Cache<String, AtomicInteger> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(2))
        .maximumSize(100_000)
        .build()

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        if (!enabled) {
            chain.doFilter(req, res)
            return
        }
        val uri = req.requestURI
        val limit = limitFor(uri)
        if (limit <= 0) {
            chain.doFilter(req, res)
            return
        }

        val userIdAttr = req.getAttribute("xr.userId")
        val actor = if (userIdAttr != null) userIdAttr.toString() else req.remoteAddr
        val minuteKey = (System.currentTimeMillis() / 60_000L).toString()
        val key = "$actor:${groupOf(uri)}:$minuteKey"

        val counter = buckets.get(key) { AtomicInteger(0) }!!
        val count = counter.incrementAndGet()
        if (count > limit) {
            res.status = 429 // 429 Too Many Requests — jakarta.servlet 상수 없음
            res.contentType = "application/problem+json;charset=UTF-8"
            res.setHeader("Retry-After", "60")
            res.writer.write(
                """{"type":"https://lemuel.co.kr/errors/E_RATE_LIMITED","title":"Too many requests","status":429,"code":"E_RATE_LIMITED"}""",
            )
            return
        }
        chain.doFilter(req, res)
    }

    private fun limitFor(uri: String): Int {
        if (uri.startsWith("/api/emotion/classify")) return classifyPerMin
        if (uri.contains("/decide") && uri.startsWith("/api/game/")) return llmRealtimePerMin
        if (uri.startsWith("/api/tts/synthesize")) return ttsMissPerMin
        if (uri.startsWith("/api/internal/") || uri.startsWith("/actuator/")) return 0
        return defaultPerMin
    }

    private fun groupOf(uri: String): String {
        if (uri.startsWith("/api/emotion/classify")) return "classify"
        if (uri.contains("/decide")) return "decide"
        if (uri.startsWith("/api/tts/synthesize")) return "tts"
        return "default"
    }
}
