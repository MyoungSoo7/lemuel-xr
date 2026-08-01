package github.lms.lemuel.xr.common.security

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 콘텐츠 endpoint 차단 — 사용자가 disclaimer 동의 안 했으면 451.
 *
 * HTTP 451 (Unavailable For Legal Reasons) — *법적으로* 제공 불가하다는 명시.
 * 클라이언트는 응답 헤더 X-Lemuel-Disclaimer-Required 보면 동의 화면 표시.
 *
 * 예외 경로 — 동의 자체 endpoint 와 위기 자원 / disclaimer 텍스트 fetch:
 * 동의 안 한 사용자도 *위기 자원에는 접근* 가능 (안전 최우선).
 */
@Component
class DisclaimerGateFilter(private val users: UserPort) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val uri: String? = req.requestURI

        if (uri == null || isAllowed(uri) || uri.startsWith("/api/internal/")) {
            chain.doFilter(req, res)
            return
        }

        val userIdAttr = req.getAttribute("xr.userId")
        if (userIdAttr !is UUID) {
            // 미인증 — JwtAuthFilter 에 위임 (그쪽에서 401 처리)
            chain.doFilter(req, res)
            return
        }

        val accepted = users.findById(userIdAttr)
            .map { it.disclaimerAcceptedAt != null }
            .orElse(false)

        if (!accepted) {
            res.status = 451 // Unavailable For Legal Reasons
            res.contentType = "application/problem+json;charset=UTF-8"
            res.setHeader("X-Lemuel-Disclaimer-Required", "true")
            res.setHeader("X-Lemuel-Crisis-Hotline-KR", "109")
            res.writer.write(
                """
                {"type":"https://lemuel.co.kr/errors/E_DISCLAIMER_REQUIRED",""" +
                    """"title":"Disclaimer acceptance required",""" +
                    """"status":451,"code":"E_DISCLAIMER_REQUIRED",""" +
                    """"detail":"lemuel-xr 는 영적 비상 대비 훈련 프로그램입니다 — 큐티가 일상 영적 양식이듯, """ +
                    """민방위 교육이 비상 대비이듯. 의료 진단·치료가 아니며 진단·치료를 대체하지 않습니다. """ +
                    """위기 시 109 (자살예방 상담전화). 시작 전 /api/auth/accept-disclaimer 호출 필요.",""" +
                    """"crisisResources":[""" +
                    """{"name":"자살예방 상담전화","phone":"109","hours":"24/7"},""" +
                    """{"name":"정신건강위기상담전화","phone":"1577-0199","hours":"24/7"}""" +
                    """]}""",
            )
            return
        }

        chain.doFilter(req, res)
    }

    private fun isAllowed(uri: String): Boolean {
        for (p in ALLOWED_PATHS) {
            if (p.endsWith("/")) {
                if (uri.startsWith(p)) return true
            } else if (uri == p || uri.startsWith("$p/")) {
                return true
            }
        }
        return false
    }

    companion object {
        /** disclaimer 미동의도 접근 가능한 경로 — 동의 흐름 + 위기 자원 + 공개 카탈로그. */
        private val ALLOWED_PATHS: Set<String> = setOf(
            "/api/auth/guest",
            "/api/auth/accept-disclaimer",
            "/api/users/me", // 본인 상태 조회는 허용 (disclaimer 상태 자체 포함)
            "/api/safety/crisis-resources", // R1 안전선 — 가장 절박한 순간엔 무조건 접근
            "/api/scripture/", // public — 본문 조회는 동의 무관
            "/api/content/topics", // 카탈로그
            "/api/tts/voices",
            "/api/config/", // input-mapping / asset-manifest
            "/actuator/health",
            "/actuator/info",
        )
    }
}
