package github.lms.lemuel.xr.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.PrintWriter
import java.io.StringWriter

/**
 * RateLimitFilter 단위 테스트 — 토큰 버킷 카운터의 각 분기를 servlet 목으로 커버.
 *
 * 분기: disabled bypass, limit<=0 bypass(내부/actuator), 그룹별 한도, 429 초과,
 * actor=userId vs remoteAddr.
 */
class RateLimitFilterTest {

    private lateinit var req: HttpServletRequest
    private lateinit var res: HttpServletResponse
    private lateinit var chain: FilterChain

    // default=2, classify=1, llm=1, tts=1 — 낮은 한도로 초과 분기 쉽게 유발.
    private fun filter(enabled: Boolean): RateLimitFilter =
        RateLimitFilter(enabled, 2, 1, 1, 1)

    @BeforeEach
    fun setUp() {
        req = mock()
        res = mock()
        chain = mock()
    }

    @Test
    fun `disabled 이면 그냥 통과`() {
        val f = filter(false)

        f.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(req, never()).requestURI
    }

    @Test
    fun `내부 경로는 한도 0 이라 통과`() {
        val f = filter(true)
        whenever(req.requestURI).thenReturn("/api/internal/reports")

        f.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `actuator 경로도 한도 0 이라 통과`() {
        val f = filter(true)
        whenever(req.requestURI).thenReturn("/actuator/health")

        f.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `userId 없으면 remoteAddr 를 actor 로 사용`() {
        val f = filter(true)
        whenever(req.requestURI).thenReturn("/api/other")
        whenever(req.getAttribute("xr.userId")).thenReturn(null)
        whenever(req.remoteAddr).thenReturn("10.0.0.5")

        f.doFilter(req, res, chain)

        verify(req).remoteAddr
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `userId 있으면 actor 로 사용`() {
        val f = filter(true)
        whenever(req.requestURI).thenReturn("/api/other")
        whenever(req.getAttribute("xr.userId")).thenReturn("user-abc")

        f.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `한도 이내면 통과시킨다`() {
        val f = filter(true)
        whenever(req.requestURI).thenReturn("/api/other")
        whenever(req.getAttribute("xr.userId")).thenReturn("actor-1")

        f.doFilter(req, res, chain) // 1
        f.doFilter(req, res, chain) // 2 (== default 2)

        verify(chain, times(2)).doFilter(req, res)
        verify(res, never()).setStatus(429)
    }

    @Test
    fun `한도 초과하면 429 와 RetryAfter`() {
        val f = filter(true)
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/emotion/classify") // classify 한도 1
        whenever(req.getAttribute("xr.userId")).thenReturn("actor-2")
        whenever(res.writer).thenReturn(PrintWriter(body))

        f.doFilter(req, res, chain) // 1 통과
        f.doFilter(req, res, chain) // 2 초과 → 429

        verify(res).setStatus(429)
        verify(res).setHeader("Retry-After", "60")
        assertThat(body.toString()).contains("E_RATE_LIMITED")
    }

    @Test
    fun `game decide 경로는 llm 한도 그룹`() {
        val f = filter(true)
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/game/abc/decide") // llm 한도 1
        whenever(req.getAttribute("xr.userId")).thenReturn("actor-3")
        whenever(res.writer).thenReturn(PrintWriter(body))

        f.doFilter(req, res, chain) // 1
        f.doFilter(req, res, chain) // 초과

        verify(res).setStatus(429)
    }

    @Test
    fun `tts synthesize 경로는 tts 한도 그룹`() {
        val f = filter(true)
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/tts/synthesize") // tts 한도 1
        whenever(req.getAttribute("xr.userId")).thenReturn("actor-4")
        whenever(res.writer).thenReturn(PrintWriter(body))

        f.doFilter(req, res, chain) // 1
        f.doFilter(req, res, chain) // 초과

        verify(res).setStatus(429)
    }

    @Test
    fun `decide 이지만 game 접두어 아니면 default 한도`() {
        // limitFor 는 "/api/game/" && "/decide" 조합만 llm — group 은 decide 지만 한도는 default.
        val f = filter(true)
        whenever(req.requestURI).thenReturn("/api/other/decide")
        whenever(req.getAttribute("xr.userId")).thenReturn("actor-5")

        f.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(res, never()).setStatus(429)
    }
}
