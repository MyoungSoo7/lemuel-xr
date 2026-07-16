package github.lms.lemuel.xr.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * X-Internal-Token 헤더 검증 — /api/internal/ 경로 보호.
 * Python AI/TTS 사이드카가 backend 의 admin API 를 호출할 때 사용.
 *
 * MessageDigest.isEqual 으로 timing attack 방지. inter-asat 의
 * InternalServiceTokenFilter 패턴 그대로 재사용.
 */
@Component
class InternalTokenFilter(
    @param:Value("\${security.internal.token}") token: String,
) : OncePerRequestFilter() {

    private val expected: ByteArray = token.toByteArray(StandardCharsets.UTF_8)

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val uri = req.requestURI
        if (!uri.startsWith(PATH_PREFIX)) {
            chain.doFilter(req, res)
            return
        }
        val provided = req.getHeader(HEADER)
        if (provided == null || !MessageDigest.isEqual(expected, provided.toByteArray(StandardCharsets.UTF_8))) {
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            res.contentType = "application/problem+json;charset=UTF-8"
            res.writer.write(
                """{"type":"https://lemuel.co.kr/errors/E_INTERNAL_TOKEN_INVALID","title":"Internal service token mismatch","status":401,"code":"E_INTERNAL_TOKEN_INVALID"}""",
            )
            return
        }
        val auth = UsernamePasswordAuthenticationToken(
            "internal", null, listOf(SimpleGrantedAuthority("ROLE_INTERNAL")),
        )
        SecurityContextHolder.getContext().authentication = auth
        chain.doFilter(req, res)
    }

    companion object {
        private const val HEADER = "X-Internal-Token"
        private const val PATH_PREFIX = "/api/internal/"
    }
}
