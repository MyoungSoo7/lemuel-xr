package github.lms.lemuel.xr.ai.grounding.adapter.out.embedding

import github.lms.lemuel.xr.ai.grounding.domain.CosineSimilarity
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * 절단 임베딩의 정규화 계약. HTTP 호출 없이 순수 함수만 본다.
 *
 * 이 테스트가 지키는 것은 "1536 차원으로 잘라 쓰되 단위 벡터로 저장한다" 는 결정이다.
 * gemini-embedding-001 은 3072 이외의 차원을 스스로 정규화하지 않으므로, 이걸 빠뜨리면
 * 벡터가 조용히 비단위 노름으로 저장된다 — pgvector 내적 경로에서만 뒤늦게 틀린 값이 나온다.
 */
class GeminiEmbeddingAdapterTest {

    @Test
    @DisplayName("정규화된 벡터의 노름은 1 이다")
    fun normalized_vector_has_unit_norm() {
        val v = floatArrayOf(3f, 4f, 12f, -84f)

        val n = GeminiEmbeddingAdapter.l2Normalize(v)

        var sum = 0.0
        for (x in n) sum += x.toDouble() * x
        assertEquals(1.0, sqrt(sum), 1e-6)
    }

    @Test
    @DisplayName("정규화는 코사인 유사도를 바꾸지 않는다 — 채점 경로에 회귀가 없음을 보장한다")
    fun normalization_preserves_cosine_similarity() {
        val a = floatArrayOf(0.2f, -0.9f, 0.4f, 0.1f)
        val b = floatArrayOf(-0.3f, 0.5f, 0.8f, -0.2f)

        val before = CosineSimilarity.cosine(a, b)
        val after = CosineSimilarity.cosine(
            GeminiEmbeddingAdapter.l2Normalize(a),
            GeminiEmbeddingAdapter.l2Normalize(b),
        )

        assertEquals(before, after, 1e-6)
    }

    @Test
    @DisplayName("크기 0 벡터는 나누지 않고 그대로 돌려준다 (NaN 방지)")
    fun zero_vector_is_returned_as_is() {
        val zero = FloatArray(8)

        val n = GeminiEmbeddingAdapter.l2Normalize(zero)

        assertArrayEquals(zero, n)
        assertTrue(n.none { it.isNaN() }, "0 벡터를 노름으로 나누면 전부 NaN 이 된다")
    }

    @Test
    @DisplayName("기본 차원은 vector(1536) 열과 pgvector HNSW 2000 차원 상한을 동시에 만족한다")
    fun default_dimensions_fit_the_column_and_the_hnsw_limit() {
        // 이 두 숫자가 어긋난 채로 오래 있었다(열 1536 vs 어댑터 3072). 다시 벌어지면 여기서 깨진다.
        assertEquals(1536, GeminiEmbeddingAdapter.DEFAULT_DIMENSIONS,
            "scripture_embeddings.embedding 은 vector(1536) 이다")
        assertTrue(GeminiEmbeddingAdapter.DEFAULT_DIMENSIONS <= 2000,
            "pgvector 는 HNSW 인덱스 대상 열을 2000 차원으로 제한한다 (운영 pgvector 0.8.2 실측)")
        assertTrue(GeminiEmbeddingAdapter.NATIVE_DIMENSIONS > 2000,
            "모델 기본 차원 3072 는 HNSW 로 색인할 수 없다 — 절단이 선택이 아니라 전제인 이유")
    }
}
