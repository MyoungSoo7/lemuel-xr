package github.lms.lemuel.xr.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.context.SecurityContextHolder
import java.io.PrintWriter
import java.io.StringWriter

/**
 * InternalTokenFilter 단위 테스트 — X-Internal-Token 검증 분기를 servlet 목으로 커버.
 *
 * 분기: 비내부 경로 pass-through, 헤더 없음 → 401, 토큰 불일치 → 401, 일치 → 인증 세팅 후 통과.
 */
class InternalTokenFilterTest {

    private lateinit var filter: InternalTokenFilter
    private lateinit var req: HttpServletRequest
    private lateinit var res: HttpServletResponse
    private lateinit var chain: FilterChain

    @BeforeEach
    fun setUp() {
        filter = InternalTokenFilter(TOKEN)
        req = mock()
        res = mock()
        chain = mock()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `비내부 경로는 통과시킨다`() {
        whenever(req.requestURI).thenReturn("/api/game/session")

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(req, never()).getHeader(any())
    }

    @Test
    fun `토큰 헤더 없으면 401`() {
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/internal/reports")
        whenever(req.getHeader("X-Internal-Token")).thenReturn(null)
        whenever(res.writer).thenReturn(PrintWriter(body))

        filter.doFilter(req, res, chain)

        verify(res).setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        verify(chain, never()).doFilter(any(), any())
        assertThat(body.toString()).contains("E_INTERNAL_TOKEN_INVALID")
    }

    @Test
    fun `토큰 불일치면 401`() {
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/internal/reports")
        whenever(req.getHeader("X-Internal-Token")).thenReturn("wrong-token")
        whenever(res.writer).thenReturn(PrintWriter(body))

        filter.doFilter(req, res, chain)

        verify(res).setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        verify(chain, never()).doFilter(any(), any())
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `토큰 일치면 ROLE_INTERNAL 인증 세팅 후 통과`() {
        whenever(req.requestURI).thenReturn("/api/internal/reports")
        whenever(req.getHeader("X-Internal-Token")).thenReturn(TOKEN)

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        val auth = SecurityContextHolder.getContext().authentication
        assertThat(auth).isNotNull()
        assertThat(auth!!.principal).isEqualTo("internal")
        assertThat(auth!!.authorities)
            .anyMatch { it.authority == "ROLE_INTERNAL" }
    }

    companion object {
        private const val TOKEN = "s3cr3t-internal-token"
    }
}
