package github.lms.lemuel.xr.common.security

import github.lms.lemuel.xr.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * 인증 실패는 **401** 이어야 한다 — 403 이면 안 된다.
 *
 * 이 파일도 사고에서 나왔다(2026-08-06). 사용자가 "감정 분석 + 본문 추천 버튼이 403 난다,
 * 제미나이 키가 죽었냐"고 제보했는데, 사이드카는 멀쩡했고 원인은 인증이었다.
 *
 * [SecurityConfig] 에 AuthenticationEntryPoint 가 없으면 Spring Security 는 미인증 요청도
 * AccessDenied 로 처리해 403 을 준다. 그런데 프론트엔드 axios 인터셉터는 **401 에만**
 * 게스트 토큰을 버리고 재발급한다(frontend/src/lib/api/client.ts). 결과적으로 30일짜리
 * 게스트 JWT 가 만료되는 순간 localStorage 의 죽은 토큰이 영구히 박혀서, 새로고침을 해도
 * 앱 전체가 403 으로 잠겼다. 사용자 계정 기능이 따로 없어 게스트 토큰이 유일한 신원이라
 * 이 고장은 곧 "앱 전체가 죽음" 이다.
 *
 * BACKEND-API-DESIGN.md §12 와 [github.lms.lemuel.xr.common.ErrorCode] 는 처음부터 이 자리를
 * E_AUTH_REQUIRED(401) 로 규정하고 있었다. 구현만 어긋나 있었고, 그걸 잡아줄 테스트가 없었다.
 */
class AuthFailureStatusTest : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    private fun client(): RestClient = RestClient.create("http://localhost:$port")

    private fun getMeExpectingFailure(bearer: String?): HttpClientErrorException =
        catchThrowableOfType(HttpClientErrorException::class.java) {
            client().get().uri("/api/users/me")
                .apply { if (bearer != null) header("Authorization", bearer) }
                .retrieve()
                .body(String::class.java)
        }

    @Test
    fun `토큰이 없으면 401 이고 403 이 아니다`() {
        val e = getMeExpectingFailure(null)

        assertThat(e.statusCode.value())
            .describedAs("403 이면 프론트엔드가 토큰을 재발급하지 않아 사용자가 영구히 잠긴다.")
            .isEqualTo(401)
        assertThat(e.responseBodyAsString).contains("E_AUTH_REQUIRED")
    }

    @Test
    fun `토큰이 쓰레기값이면 401 이다`() {
        val e = getMeExpectingFailure("Bearer not-a-jwt")

        assertThat(e.statusCode.value()).isEqualTo(401)
        assertThat(e.responseBodyAsString).contains("E_AUTH_REQUIRED")
    }

    @Test
    fun `Bearer 접두어가 없어도 401 이다`() {
        val e = getMeExpectingFailure("just-a-raw-token")

        assertThat(e.statusCode.value()).isEqualTo(401)
    }

    @Test
    fun `만료된 서명 형식의 JWT 도 401 이다`() {
        // 서명은 맞을 리 없지만 형식은 JWT — JwtAuthFilter 가 JwtException 으로 삼키는 경로.
        val expired = "Bearer eyJhbGciOiJIUzI1NiJ9." +
            "eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDAiLCJleHAiOjE2MDAwMDAwMDB9.zzzz"
        val e = getMeExpectingFailure(expired)

        assertThat(e.statusCode.value()).isEqualTo(401)
    }

    @Test
    fun `정상 게스트 토큰이면 통과한다`() {
        val guest = client().post().uri("/api/auth/guest")
            .body(mapOf("deviceType" to "web"))
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val token = guest["token"] as String

        val me = client().get().uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})!!

        // 401 을 고치느라 정상 경로까지 막지 않았는지 — 이게 없으면 위 4개는 공허하게 통과한다.
        assertThat(me).containsEntry("userType", "guest")
    }

    @Test
    fun `공개 endpoint 는 여전히 토큰 없이 열려 있다`() {
        val body = client().get().uri("/api/safety/crisis-resources")
            .retrieve()
            .body(String::class.java)

        assertThat(body).isNotNull()
    }
}
