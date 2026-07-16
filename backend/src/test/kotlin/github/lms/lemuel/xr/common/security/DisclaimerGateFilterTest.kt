package github.lms.lemuel.xr.common.security

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.User
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

/**
 * DisclaimerGateFilter 단위 테스트 — servlet 목으로 각 분기를 커버.
 *
 * 분기: (1) 허용 경로 pass-through, (2) 내부 경로 bypass, (3) 미인증 pass-through,
 * (4) 동의 완료 pass-through, (5) 미동의 → 451.
 */
class DisclaimerGateFilterTest {

    private lateinit var users: UserPort
    private lateinit var filter: DisclaimerGateFilter
    private lateinit var req: HttpServletRequest
    private lateinit var res: HttpServletResponse
    private lateinit var chain: FilterChain

    @BeforeEach
    fun setUp() {
        users = mock()
        filter = DisclaimerGateFilter(users)
        req = mock()
        res = mock()
        chain = mock()
    }

    private fun user(disclaimerAcceptedAt: OffsetDateTime?): User =
        User(
            UUID.randomUUID(), null, null, "guest", "ext-1",
            null, null, null, null, null, null,
            disclaimerAcceptedAt, if (disclaimerAcceptedAt == null) null else "1.0", null,
        )

    @Test
    fun `허용 경로는 통과시킨다`() {
        whenever(req.requestURI).thenReturn("/api/safety/crisis-resources")

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(users, never()).findById(any())
    }

    @Test
    fun `접두어 허용 경로도 통과시킨다`() {
        // "/api/scripture/" 는 trailing slash 접두어 규칙(startsWith) 커버.
        whenever(req.requestURI).thenReturn("/api/scripture/john/3/16")

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `정확 일치 경로 뒤에 슬래시 하위경로도 허용`() {
        // "/api/users/me" 는 non-slash 규칙 — equals 또는 startsWith(p+"/") 커버.
        whenever(req.requestURI).thenReturn("/api/users/me/status")

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `내부 경로는 bypass`() {
        whenever(req.requestURI).thenReturn("/api/internal/reports")

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(users, never()).findById(any())
    }

    @Test
    fun `uri null 이면 통과시킨다`() {
        whenever(req.requestURI).thenReturn(null)

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `미인증이면 JwtAuthFilter 에 위임하며 통과`() {
        whenever(req.requestURI).thenReturn("/api/game/session")
        whenever(req.getAttribute("xr.userId")).thenReturn(null)

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(users, never()).findById(any())
    }

    @Test
    fun `userId 속성이 UUID 아니면 미인증 취급하여 통과`() {
        whenever(req.requestURI).thenReturn("/api/game/session")
        whenever(req.getAttribute("xr.userId")).thenReturn("not-a-uuid")

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
    }

    @Test
    fun `동의 완료 사용자는 통과`() {
        val uid = UUID.randomUUID()
        whenever(req.requestURI).thenReturn("/api/game/session")
        whenever(req.getAttribute("xr.userId")).thenReturn(uid)
        whenever(users.findById(uid)).thenReturn(Optional.of(user(OffsetDateTime.now())))

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        verify(res, never()).setStatus(451)
    }

    @Test
    fun `미동의 사용자는 451 과 헤더 반환`() {
        val uid = UUID.randomUUID()
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/game/session")
        whenever(req.getAttribute("xr.userId")).thenReturn(uid)
        whenever(users.findById(uid)).thenReturn(Optional.of(user(null)))
        whenever(res.writer).thenReturn(PrintWriter(body))

        filter.doFilter(req, res, chain)

        verify(res).setStatus(451)
        verify(res).setHeader("X-Lemuel-Disclaimer-Required", "true")
        verify(res).setHeader("X-Lemuel-Crisis-Hotline-KR", "1393")
        verify(chain, never()).doFilter(any(), any())
        assertThat(body.toString()).contains("E_DISCLAIMER_REQUIRED")
    }

    @Test
    fun `존재하지 않는 사용자는 미동의로 간주하여 451`() {
        val uid = UUID.randomUUID()
        val body = StringWriter()
        whenever(req.requestURI).thenReturn("/api/game/session")
        whenever(req.getAttribute("xr.userId")).thenReturn(uid)
        whenever(users.findById(uid)).thenReturn(Optional.empty())
        whenever(res.writer).thenReturn(PrintWriter(body))

        filter.doFilter(req, res, chain)

        verify(res).setStatus(451)
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `미허용 경로 동의완료는 한번만 체인`() {
        val uid = UUID.randomUUID()
        whenever(req.requestURI).thenReturn("/api/emotion/classify")
        whenever(req.getAttribute("xr.userId")).thenReturn(uid)
        whenever(users.findById(uid)).thenReturn(Optional.of(user(OffsetDateTime.now())))

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
    }
}
