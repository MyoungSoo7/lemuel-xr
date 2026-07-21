package github.lms.lemuel.xr.ai.grounding.adapter.out.embedding

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Google Generative Language 임베딩 어댑터 — [EmbeddingPort] 구현. 검증 harness 전용.
 * `gemini-embedding-001:embedContent` 를 텍스트당 1회 호출한다(픽스처 소량이므로 배치 불필요).
 * 키는 GEMINI_API_KEY. 라이브 경로 배선은 이 프로토타입 범위 밖.
 * (2026-07-19 라이브 확인: text-embedding-004 는 이 키의 v1beta 에서 404 → gemini-embedding-001 사용, 3072차원.)
 *
 * 스프링 빈 아님(2026-07-19 결정): 검증 harness 가 apiKey/model 을 넘겨 직접 생성한다.
 */
class GeminiEmbeddingAdapter(
    private val apiKey: String,
    private val model: String = "gemini-embedding-001",
) : EmbeddingPort {

    private val client: RestClient = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .build()

    private data class Part(val text: String)
    private data class Content(val parts: List<Part>)
    private data class EmbedRequest(val model: String, val content: Content)
    private data class Embedding(val values: List<Double> = emptyList())
    private data class EmbedResponse(val embedding: Embedding = Embedding())

    override fun embed(texts: List<String>): List<FloatArray> = texts.map { text ->
        val resp = client.post()
            .uri("/models/{m}:embedContent?key={k}", model, apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(EmbedRequest("models/$model", Content(listOf(Part(text)))))
            .retrieve()
            .body(EmbedResponse::class.java) ?: error("빈 임베딩 응답")
        FloatArray(resp.embedding.values.size) { i -> resp.embedding.values[i].toFloat() }
    }
}
