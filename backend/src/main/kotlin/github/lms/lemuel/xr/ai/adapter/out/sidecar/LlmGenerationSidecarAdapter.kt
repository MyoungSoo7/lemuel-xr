package github.lms.lemuel.xr.ai.adapter.out.sidecar

import github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.common.sidecar.SidecarHttp
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * [LlmGenerationPort] 구현 — Python AI 사이드카 (FastAPI + LangChain) 의
 * **LLM 생성** HTTP 클라이언트.
 *
 * 단일 책임: `/ai/generate` (promptKey + variables → 응답 생성) 하나만 담당.
 * 감정 분류(/classify-emotion) 는 emotion 컨텍스트의
 * `EmotionClassificationClient` 로 분리됨 (SRP).
 *
 * WebClient 사용 — 동기 호출은 .block() 으로 마무리. timeout 은 application.yml.
 * 키 보호: provider API 키는 사이드카에만, backend 는 모름.
 *
 * 헥사고날: 구체 HTTP 세부(WebClient·엔드포인트·타임아웃) 는 이 adapter 안에만 존재하고,
 * 유즈케이스는 [LlmGenerationPort] 에만 의존한다.
 */
@Component
class LlmGenerationSidecarAdapter(
    @Value("\${ai.base-url}") baseUrl: String,
    @Value("\${ai.timeout-ms:30000}") timeoutMs: Long,
    @Value("\${ai.internal-token}") private val internalToken: String,
    @Value("\${ai.max-response-bytes:4194304}") maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) : LlmGenerationPort {

    private val client: WebClient = SidecarHttp.client(baseUrl, maxResponseBytes)
    private val timeout: Duration = Duration.ofMillis(timeoutMs)

    /** /ai/generate — promptKey + variables 로 응답 생성. */
    override fun generate(
        purpose: String,
        promptKey: String,
        variables: Map<String, Any?>,
    ): LlmGenerationPort.GenerateResult =
        SidecarHttp.post(
            client, "/ai/generate",
            mapOf("purpose" to purpose, "promptKey" to promptKey, "variables" to variables),
            mapOf("X-Internal-Token" to internalToken),
            timeout, ErrorCode.E_LLM_UPSTREAM_FAIL,
        ) { resp ->
            LlmGenerationPort.GenerateResult(
                resp["text"] as String?,
                resp["provider"] as String?,
                resp["model"] as String?,
                resp["promptTokens"] as Int?,
                resp["completionTokens"] as Int?,
                resp["cached"] == true,
            )
        }

    companion object {
        /**
         * 4MiB. 긴 생성 응답이 WebClient 기본 한도(256KB)를 넘는 순간
         * 사이드카는 200 인데 백엔드만 502 가 되는 사고가 난다 — TTS 에서 실제로 겪었다.
         * [SidecarHttp.client] 참조.
         */
        const val DEFAULT_MAX_RESPONSE_BYTES: Int = 4 * 1024 * 1024
    }
}
