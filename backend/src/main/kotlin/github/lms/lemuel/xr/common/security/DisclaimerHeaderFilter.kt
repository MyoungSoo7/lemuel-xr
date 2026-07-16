package github.lms.lemuel.xr.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Layer 5 — 모든 응답에 *치료 도구가 아님 + 위기 hotline + 본 응답 AI 여부* 메타 헤더 박기.
 *
 * 사용자 눈에는 안 보이지만:
 * - Cloudflare WAF·인증 심사 자동 입증
 * - 법적 분쟁 시 *서버는 항상 disclaimer 를 알렸다* 는 증거
 * - 클라이언트(Unity) 가 *AI 라벨* 자동 표시 트리거
 */
@Component
class DisclaimerHeaderFilter : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        // 응답 commit 전에 헤더 박음.
        res.setHeader("X-Lemuel-Disclaimer", "not-medical-device")
        res.setHeader("X-Lemuel-Crisis-Hotline-KR", "1393")
        res.setHeader("X-Lemuel-Disclaimer-Version", "1.0")
        chain.doFilter(req, res)
    }
}
