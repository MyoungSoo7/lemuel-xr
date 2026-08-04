package github.lms.lemuel.xr.common.security

import github.lms.lemuel.xr.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * `/actuator/prometheus` 가 **인증 없이** 200 을 주는지 고정한다.
 *
 * 이 파일이 존재하는 이유도 사고다. Service 라벨을 고쳐 ServiceMonitor 가 드디어 대상을
 * 잡았는데(2026-08-05), 스크레이프가 곧바로 실패했다:
 *
 * ```
 * up{job="lemuel-xr-backend"} = 0
 * lastError: "server returned HTTP status 403"
 * ```
 *
 * [SecurityConfig] 의 `.anyRequest().authenticated()` 에 걸린 것이다. actuator 중
 * health·info 만 permitAll 이었고 prometheus 는 빠져 있었다. 노출(exposure.include)은
 * 되어 있었으니 "endpoint 는 있는데 Security 가 막는" 상태 — 설정 파일만 봐서는
 * 열린 것처럼 보이는 조합이다.
 *
 * prometheus-operator 의 ServiceMonitor 는 basicAuth·Bearer 만 지원해서 이 앱의
 * `X-Internal-Token` 을 태울 수 없다. 즉 이 endpoint 를 인증 뒤로 되돌리는 순간
 * *지표 수집 전체가 조용히 멈춘다* — 그리고 지표가 멈췄다는 사실 자체가 지표로는
 * 안 보인다. 그래서 코드로 못 박는다.
 *
 * 함께 검사하는 것: `/actuator/metrics` 는 여전히 막혀 있어야 한다. 이번에 연 것은
 * 스크레이프 경로 하나뿐이고, actuator 를 통째로 연 것이 아니다.
 */
class ActuatorPrometheusAccessTest : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    private fun client(): RestClient = RestClient.create("http://localhost:$port")

    @Test
    fun `인증 없이 prometheus 스크레이프가 가능하다`() {
        val body = client().get().uri("/actuator/prometheus")
            .retrieve()
            .body(String::class.java)

        // 본문이 실제 Prometheus 텍스트 포맷인지 — 200 이어도 빈 응답이면 의미가 없다.
        assertThat(body)
            .describedAs(
                "ServiceMonitor 는 익명으로 스크레이프한다. 여기가 401/403 이면 " +
                    "up{job=\"lemuel-xr-backend\"}=0 이 되고 grounding.goldenset.* 를 포함한 " +
                    "모든 지표가 Prometheus 에 영영 도달하지 않는다.",
            )
            .contains("jvm_memory_used_bytes")
    }

    @Test
    fun `다른 actuator endpoint 는 여전히 인증을 요구한다`() {
        assertThatThrownBy {
            client().get().uri("/actuator/metrics").retrieve().body(String::class.java)
        }
            .describedAs("이번에 연 것은 스크레이프 경로 하나다. actuator 를 통째로 열면 안 된다.")
            .isInstanceOf(HttpClientErrorException::class.java)
            .extracting { (it as HttpClientErrorException).statusCode }
            .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
    }
}
