package github.lms.lemuel.xr.ai.grounding.adapter.out.embedding

import com.fasterxml.jackson.annotation.JsonInclude
import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import kotlin.math.sqrt

/**
 * Google Generative Language 임베딩 어댑터 — [EmbeddingPort] 구현.
 * `gemini-embedding-001:embedContent` 를 텍스트당 1회 호출한다(픽스처 소량이므로 배치 불필요).
 * 키는 GEMINI_API_KEY.
 * (2026-07-19 라이브 확인: text-embedding-004 는 이 키의 v1beta 에서 404 → gemini-embedding-001 사용.)
 *
 * 배선: `grounding.eval.enabled=true` 일 때만 `GroundingEvalConfig` 가 빈으로 만든다.
 * 검증 harness(테스트)는 apiKey/model 을 넘겨 직접 생성한다.
 *
 * ## 왜 3072 가 아니라 1536 을 요청하는가 (2026-08-12)
 * 이 모델의 기본 출력은 3072 차원이다. 반면 임베딩이 들어갈 자리인
 * `scripture_embeddings.embedding` 은 `vector(1536)` 이고 그 위에 HNSW 인덱스가 있다.
 * 이 차이가 오래 방치돼 있었다 — 열은 1536, 어댑터는 3072 를 뱉는 상태.
 *
 * 맞추는 방향을 열 쪽(3072 로 확장)이 아니라 어댑터 쪽으로 잡은 이유는 pgvector 의 제약이다.
 * HNSW 인덱스 대상 열은 2000 차원을 넘을 수 없다 — 운영 DB(pgvector 0.8.2)에서 직접 확인했다:
 * `column cannot have more than 2000 dimensions for hnsw index`.
 * 즉 3072 로 넓히면 이 테이블은 인덱스 없는 순차 스캔이 된다.
 *
 * 대신 Google 이 문서화한 `outputDimensionality` 로 자른다. 두 임베딩 모델 모두 Matryoshka
 * Representation Learning 으로 학습돼 앞쪽 절단이 그 자체로 유효한 임베딩이며, 문서는
 * 768/1536/3072 를 권장 값으로 제시한다. 다만 **`gemini-embedding-001` 은 3072 이외의 차원을
 * 직접 L2 정규화해야 한다**(자동 정규화는 `gemini-embedding-2` 만). 그래서 [l2Normalize] 가 있다.
 * 출처: https://ai.google.dev/gemini-api/docs/embeddings — "Controlling embedding size".
 */
class GeminiEmbeddingAdapter(
    private val apiKey: String,
    private val model: String = "gemini-embedding-001",
    private val outputDimensionality: Int = DEFAULT_DIMENSIONS,
) : EmbeddingPort {

    private val client: RestClient = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .build()

    private data class Part(val text: String)
    private data class Content(val parts: List<Part>)

    /** `outputDimensionality` 는 null 이면 아예 직렬화하지 않는다 — 아래 [embed] 주석 참고. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private data class EmbedRequest(
        val model: String,
        val content: Content,
        val outputDimensionality: Int?,
    )

    private data class Embedding(val values: List<Double> = emptyList())
    private data class EmbedResponse(val embedding: Embedding = Embedding())

    override fun embed(texts: List<String>): List<FloatArray> = texts.map { text ->
        val resp = client.post()
            .uri("/models/{m}:embedContent?key={k}", model, apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                EmbedRequest(
                    model = "models/$model",
                    content = Content(listOf(Part(text))),
                    // 기본 차원을 원할 땐 필드를 보내지 않는다. 값을 명시하지 않는 편이
                    // 예전 동작(3072)과 바이트 단위로 같아서, 이 변경이 회귀를 만들지 않는다.
                    outputDimensionality = outputDimensionality.takeIf { it != NATIVE_DIMENSIONS },
                ),
            )
            .retrieve()
            .body(EmbedResponse::class.java) ?: error("빈 임베딩 응답")

        val raw = FloatArray(resp.embedding.values.size) { i -> resp.embedding.values[i].toFloat() }
        // 차원이 조용히 어긋나면 저장 시점(vector(1536))에야 터진다. 여기서 먼저 잡는다.
        check(raw.size == outputDimensionality) {
            "임베딩 차원 불일치: 요청 $outputDimensionality, 응답 ${raw.size} ($model)"
        }
        if (outputDimensionality == NATIVE_DIMENSIONS) raw else l2Normalize(raw)
    }

    companion object {
        /** 모델이 기본으로 내는 차원. 이 차원일 때만 응답이 이미 정규화돼 있다. */
        const val NATIVE_DIMENSIONS = 3072

        /**
         * 이 프로젝트의 기본 차원. `scripture_embeddings.embedding vector(1536)` 및
         * pgvector 의 HNSW 2000 차원 상한에 맞춘 값이다. 바꾸려면 마이그레이션이 함께 가야 한다.
         */
        const val DEFAULT_DIMENSIONS = 1536

        /**
         * 단위 벡터로 만든다. 코사인 유사도는 크기에 불변이라
         * `CosineSimilarity` 를 쓰는 채점 경로의 점수는 이걸로 달라지지 않는다.
         * 필요한 쪽은 저장이다 — pgvector 의 내적 연산자(`<#>`)나 단위 노름을 전제하는 소비자는
         * 정규화되지 않은 절단 벡터에서 조용히 틀린 값을 낸다. Google 문서도 `gemini-embedding-001`
         * 에 대해 3072 이외 차원의 수동 정규화를 명시적으로 요구한다.
         *
         * 크기 0 벡터는 나누지 않고 그대로 돌려준다(NaN 방지).
         */
        internal fun l2Normalize(v: FloatArray): FloatArray {
            var sumOfSquares = 0.0
            for (x in v) sumOfSquares += x.toDouble() * x
            val norm = sqrt(sumOfSquares)
            if (norm == 0.0) return v
            return FloatArray(v.size) { i -> (v[i] / norm).toFloat() }
        }
    }
}
