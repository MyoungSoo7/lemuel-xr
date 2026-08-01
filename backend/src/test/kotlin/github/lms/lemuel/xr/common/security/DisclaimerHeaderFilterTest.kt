package github.lms.lemuel.xr.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * DisclaimerHeaderFilter 단위 테스트 — 모든 응답에 disclaimer 메타 헤더 3종을 박고 체인 통과.
 */
class DisclaimerHeaderFilterTest {

    @Test
    fun `헤더 3종을 박고 체인을 통과시킨다`() {
        val filter = DisclaimerHeaderFilter()
        val req: HttpServletRequest = mock()
        val res: HttpServletResponse = mock()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)

        verify(res).setHeader("X-Lemuel-Disclaimer", "not-medical-device")
        verify(res).setHeader("X-Lemuel-Crisis-Hotline-KR", "109")
        verify(res).setHeader("X-Lemuel-Disclaimer-Version", "1.0")
        verify(chain).doFilter(req, res)
    }
}
