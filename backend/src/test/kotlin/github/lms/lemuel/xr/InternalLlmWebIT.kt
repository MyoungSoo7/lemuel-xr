package github.lms.lemuel.xr

import github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * ai/adapter/in/web/InternalLlmController 통합 테스트.
 *
 * /api/internal/llm/generate 는 X-Internal-Token 필요 (InternalTokenFilter, ROLE_INTERNAL).
 * 외부 AI 사이드카([LlmGenerationPort]) 는 [MockitoBean] 으로 목킹 — 실제 LLM 호출 없음.
 * cache miss → 사이드카 호출(cached=false) → 동일 요청 캐시 히트(cached=true) → 토큰 누락 401 커버.
 * ai.generation.enabled 는 기본 true 라 miss 시 사이드카를 실제로 호출한다.
 */
class InternalLlmWebIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    @MockitoBean
    lateinit var sidecar: LlmGenerationPort

    private fun internal(): RestClient = RestClient.builder()
        .baseUrl("http://localhost:$port")
        .defaultHeader("X-Internal-Token", INTERNAL_TOKEN)
        .build()

    @Test
    fun `generate 최초는 사이드카 호출 cached false 그리고 재요청은 cached true`() {
        whenever(sidecar.generate(any(), any(), any()))
            .thenReturn(
                LlmGenerationPort.GenerateResult(
                    "생성된 묵상 본문", "anthropic", "claude-x", 11, 22, false,
                ),
            )

        // 유니크 promptKey → 이 테스트만의 캐시 키
        val promptKey = "internal.llm.it." + System.nanoTime()
        val body = mapOf(
            "purpose" to "meditation",
            "promptKey" to promptKey,
            "variables" to mapOf("k" to "v"),
        )

        val first = internal().post().uri("/api/internal/llm/generate")
            .body(body).retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(first).containsEntry("text", "생성된 묵상 본문")
            .containsEntry("cached", false)
            .containsEntry("provider", "anthropic")
            .containsEntry("aiGenerated", true)
            .containsEntry("aiLabel", "AI 보조 — 본문은 성경 참조")

        // 동일 입력 → DB 캐시 히트, 사이드카 재호출 없이 cached=true.
        val second = internal().post().uri("/api/internal/llm/generate")
            .body(body).retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(second).containsEntry("text", "생성된 묵상 본문")
            .containsEntry("cached", true)
    }

    @Test
    fun `generate 토큰 없으면 401`() {
        val noToken = RestClient.create("http://localhost:$port")
        try {
            noToken.post().uri("/api/internal/llm/generate")
                .body(mapOf("purpose" to "meditation", "promptKey" to "k", "variables" to mapOf<String, Any>()))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 401")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(401)
            assertThat(e.responseBodyAsString).contains("E_INTERNAL_TOKEN_INVALID")
        }
    }

    @Test
    fun `generate 잘못된 토큰이면 401`() {
        val badToken = RestClient.builder()
            .baseUrl("http://localhost:$port")
            .defaultHeader("X-Internal-Token", "wrong-token")
            .build()
        try {
            badToken.post().uri("/api/internal/llm/generate")
                .body(mapOf("purpose" to "meditation", "promptKey" to "k", "variables" to mapOf<String, Any>()))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 401")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(401)
        }
    }

    companion object {
        // application.yml 의 security.internal.token 기본값.
        private const val INTERNAL_TOKEN = "dev-internal-svc-token-rotate-in-prod"
    }
}
