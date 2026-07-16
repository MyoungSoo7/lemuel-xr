package github.lms.lemuel.xr.common.jwt

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Authorization: Bearer <jwt> 헤더 검증 → SecurityContext 에 Principal 적재. */
@Component
class JwtAuthFilter(private val verifier: JwtVerifier) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val header = req.getHeader(HEADER)
        if (header != null && header.startsWith(PREFIX)) {
            val token = header.substring(PREFIX.length).trim()
            try {
                val p = verifier.verify(token)
                val auth = UsernamePasswordAuthenticationToken(
                    p, null, listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                SecurityContextHolder.getContext().authentication = auth
                req.setAttribute("xr.userId", p.userId)
                req.setAttribute("xr.deviceType", p.deviceType)
            } catch (ignored: JwtException) {
                // 검증 실패는 SecurityFilterChain 의 익명/거부 정책으로 위임.
            }
        }
        chain.doFilter(req, res)
    }

    companion object {
        private const val HEADER = "Authorization"
        private const val PREFIX = "Bearer "
    }
}
