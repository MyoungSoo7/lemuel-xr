package github.lms.lemuel.xr.ai.application.port.out

/**
 * LLM 생성 아웃바운드 포트 (DIP).
 *
 * 애플리케이션 계층([github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase])
 * 이 구체 HTTP 클라이언트(WebClient) 나 사이드카 프로토콜을 알지 못하도록 격리한다.
 * 구현체는 `ai/adapter/out/sidecar/LlmGenerationSidecarAdapter`.
 *
 * 키 보호: provider API 키는 Python AI 사이드카에만, backend 는 모름.
 */
interface LlmGenerationPort {

    /** promptKey + variables 로 응답을 생성한다 (miss 경로). 실패 시 `E_LLM_UPSTREAM_FAIL`. */
    fun generate(purpose: String, promptKey: String, variables: Map<String, Any?>): GenerateResult

    /** 사이드카 `/ai/generate` 응답 계약. */
    data class GenerateResult(
        val text: String?,
        val provider: String?,
        val model: String?,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val cached: Boolean,
    )
}
