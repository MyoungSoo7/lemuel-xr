package github.lms.lemuel.xr.ai.grounding.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CosineSimilarityTest {

    @Test
    fun `identical vectors have cosine 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertThat(CosineSimilarity.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `orthogonal vectors have cosine 0`() {
        assertThat(CosineSimilarity.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
            .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `opposite vectors have cosine -1`() {
        assertThat(CosineSimilarity.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)))
            .isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `zero magnitude vector yields 0 not NaN`() {
        assertThat(CosineSimilarity.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f))).isEqualTo(0.0)
    }

    @Test
    fun `length mismatch throws`() {
        assertThatThrownBy { CosineSimilarity.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
