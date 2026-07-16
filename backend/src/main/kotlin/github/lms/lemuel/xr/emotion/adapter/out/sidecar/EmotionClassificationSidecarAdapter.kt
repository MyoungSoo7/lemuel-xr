package github.lms.lemuel.xr.emotion.adapter.out.sidecar

import github.lms.lemuel.xr.emotion.application.port.out.EmotionClassificationPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Python AI 사이드카 (`ai/`) 의 **감정 분류** HTTP 어댑터 — [EmotionClassificationPort] 구현.
 *
 * 단일 책임: `POST /classify-emotion` (`{text}` → `{emotion, confidence}`) 하나만
 * 담당. LLM 생성(/ai/generate) 은 ai 컨텍스트의
 * `github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort` 가 담당 (SRP).
 *
 * 사이드카 URL 은 `AI_BASE_URL` 로 주입 (k8s 에선 lemuel-xr-ai:8000). 사이드카 호출/파싱
 * 실패 시 `null` 을 반환하고, 도메인 fallback(CONFUSED) 판단은 호출자
 * (`EmotionClassifierService`) 에 맡긴다. 감정 분류의 **단일 네트워크 경로**.
 */
@Component
class EmotionClassificationSidecarAdapter(
    @Value("\${ai.base-url:http://localhost:8000}") aiBaseUrl: String,
) : EmotionClassificationPort {

    private val ai: RestClient = RestClient.builder().baseUrl(aiBaseUrl).build()

    private data class AiRequest(val text: String)

    override fun classify(rawText: String): EmotionClassificationPort.AiResponse? =
        try {
            ai.post()
                .uri("/classify-emotion")
                .contentType(MediaType.APPLICATION_JSON)
                .body(AiRequest(rawText))
                .retrieve()
                .body(EmotionClassificationPort.AiResponse::class.java)
        } catch (e: Exception) {
            log.warn("AI 사이드카 호출 실패, CONFUSED fallback: {}", e.message)
            null
        }

    companion object {
        private val log = LoggerFactory.getLogger(EmotionClassificationSidecarAdapter::class.java)
    }
}
