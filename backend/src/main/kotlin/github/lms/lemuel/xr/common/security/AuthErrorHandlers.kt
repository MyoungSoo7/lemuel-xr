package github.lms.lemuel.xr.common.security

import github.lms.lemuel.xr.common.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * 인증 실패(401)와 권한 부족(403)을 갈라 준다.
 *
 * 이걸 안 달면 Spring Security 는 *둘 다 403* 으로 뭉갠다. [JwtAuthFilter] 는 토큰이
 * 없거나 만료면 조용히 익명으로 흘려보내고, 뒤에서 `.anyRequest().authenticated()` 가
 * AccessDenied 로 처리하기 때문이다. BACKEND-API-DESIGN.md §12 는 이 자리를
 * E_AUTH_REQUIRED(401) 로 규정하고 [ErrorCode] 에도 그렇게 적혀 있는데 구현만 어긋나 있었다.
 *
 * 이건 문서 위반 이상의 실제 고장이었다. 프론트엔드 axios 인터셉터는 **401 일 때만**
 * 게스트 토큰을 버리고 재발급한다(frontend/src/lib/api/client.ts). 403 이 오면 그냥
 * 에러로 던진다. 그래서 30일짜리 게스트 JWT 가 만료되면 localStorage 에 죽은 토큰이
 * 영구히 박혀, 새로고침을 해도 계속 403 이 났다 — 사용자 입장에선 "감정 분석 + 본문 추천"
 * 버튼이 그냥 고장난 것으로 보였다(2026-08-06 제보).
 */
@Component
class RestAuthenticationEntryPoint : AuthenticationEntryPoint {

    /** 인증 정보가 없거나 유효하지 않다 — 클라이언트는 토큰을 다시 받아야 한다. */
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = writeProblem(response, ErrorCode.E_AUTH_REQUIRED)
}

// 인증은 됐는데 권한이 모자란 경우 — 예: 사용자 JWT 로 내부 전용(ROLE_INTERNAL) 경로 호출.
@Component
class RestAccessDeniedHandler : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = writeProblem(response, ErrorCode.E_FORBIDDEN)
}

/** RFC 7807 — 다른 필터들(DisclaimerGateFilter·RateLimitFilter)과 같은 모양으로 맞춘다. */
private fun writeProblem(res: HttpServletResponse, code: ErrorCode) {
    if (res.isCommitted) return
    res.status = code.httpStatus.value()
    res.contentType = "application/problem+json;charset=UTF-8"
    res.writer.write(
        """{"type":"https://lemuel.co.kr/errors/${code.name}",""" +
            """"title":"${code.defaultTitle}",""" +
            """"status":${code.httpStatus.value()},"code":"${code.name}"}""",
    )
}
