package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EvaluateGroundingUseCaseTest {

    private val metrics: GroundingMetricsPort = mock()
    private val policy = GroundingPolicy(similarityThreshold = 0.9, maxUnsupportedRate = 0.0)

    // 문장/본문에 명시적 벡터를 부여: grounded 문장은 본문과 동일 벡터(cosine≈1),
    // ungrounded 문장은 직교 벡터(cosine≈0).
    private val grounded = floatArrayOf(1f, 0f)
    private val ungrounded = floatArrayOf(0f, 1f)

    private fun useCase(vectors: Map<String, FloatArray>, fake: FakeEmbeddingAdapter = FakeEmbeddingAdapter(vectors)) =
        EvaluateGroundingUseCase(fake, metrics)

    @Test
    fun `all sentences grounded yields ACCEPTED`() {
        val vectors = mapOf("근거 문장." to grounded, "본문 텍스트" to grounded)
        val verdict = useCase(vectors).evaluate(
            "meditation", "근거 문장.", listOf(Passage("욥 42:5", "본문 텍스트")), policy,
        )
        assertThat(verdict.status).isEqualTo(GroundingStatus.ACCEPTED)
        assertThat(verdict.unsupportedRate).isEqualTo(0.0)
        assertThat(verdict.sentenceResults.single().bestPassageRef).isEqualTo("욥 42:5")
        verify(metrics).evaluated("meditation")
        verify(metrics).unsupportedRate("meditation", 0.0)
    }

    @Test
    fun `an ungrounded sentence yields REJECTED and rejected metric`() {
        val vectors = mapOf("빗나간 문장." to ungrounded, "본문 텍스트" to grounded)
        val verdict = useCase(vectors).evaluate(
            "meditation", "빗나간 문장.", listOf(Passage("욥 42:5", "본문 텍스트")), policy,
        )
        assertThat(verdict.status).isEqualTo(GroundingStatus.REJECTED)
        assertThat(verdict.unsupportedRate).isEqualTo(1.0)
        verify(metrics).rejected("meditation")
    }

    @Test
    fun `empty passages yields NO_EVIDENCE`() {
        val verdict = useCase(emptyMap()).evaluate("meditation", "아무 문장.", emptyList(), policy)
        assertThat(verdict.status).isEqualTo(GroundingStatus.NO_EVIDENCE)
        assertThat(verdict.sentenceResults).hasSize(1)
    }

    @Test
    fun `blank text yields INCONCLUSIVE`() {
        val verdict = useCase(emptyMap()).evaluate("meditation", "   \n ", listOf(Passage("x", "y")), policy)
        assertThat(verdict.status).isEqualTo(GroundingStatus.INCONCLUSIVE)
        verify(metrics).inconclusive("meditation")
    }

    @Test
    fun `embedding failure yields INCONCLUSIVE and never throws`() {
        val fake = FakeEmbeddingAdapter(mapOf("문장." to grounded, "본문" to grounded)).apply { failNext = true }
        val verdict = EvaluateGroundingUseCase(fake, metrics)
            .evaluate("meditation", "문장.", listOf(Passage("r", "본문")), policy)
        assertThat(verdict.status).isEqualTo(GroundingStatus.INCONCLUSIVE)
        verify(metrics).inconclusive("meditation")
    }

    @Test
    fun `mixed grounding computes fractional unsupportedRate`() {
        // 2 문장 중 1 개만 미근거 → rate 0.5. maxUnsupportedRate 0.5 정책이면 ACCEPTED.
        val tolerant = GroundingPolicy(similarityThreshold = 0.9, maxUnsupportedRate = 0.5)
        val vectors = mapOf("좋은 문장." to grounded, "빗나간 문장." to ungrounded, "본문" to grounded)
        val verdict = useCase(vectors).evaluate(
            "meditation", "좋은 문장. 빗나간 문장.", listOf(Passage("r", "본문")), tolerant,
        )
        assertThat(verdict.unsupportedRate).isEqualTo(0.5)
        assertThat(verdict.status).isEqualTo(GroundingStatus.ACCEPTED)
    }
}
