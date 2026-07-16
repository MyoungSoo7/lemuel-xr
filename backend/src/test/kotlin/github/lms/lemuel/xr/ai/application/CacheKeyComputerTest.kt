package github.lms.lemuel.xr.ai.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CacheKeyComputerTest {

    private val keyer = CacheKeyComputer()

    @Test
    fun `동일 promptKey 와 variables 는 동일 키`() {
        val k1 = keyer.compute("joseph.s2.monologue", mapOf("savePercentage" to "1/3"))
        val k2 = keyer.compute("joseph.s2.monologue", mapOf("savePercentage" to "1/3"))
        assertThat(k1).isEqualTo(k2)
    }

    @Test
    fun `변수 순서 달라도 같은 키`() {
        val k1 = keyer.compute("k", mapOf("a" to 1, "b" to 2))
        val k2 = keyer.compute("k", mapOf("b" to 2, "a" to 1))
        assertThat(k1).isEqualTo(k2)
    }

    @Test
    fun `다른 promptKey 다른 키`() {
        val k1 = keyer.compute("a", emptyMap())
        val k2 = keyer.compute("b", emptyMap())
        assertThat(k1).isNotEqualTo(k2)
    }

    @Test
    fun `null variables 도 안전`() {
        val k = keyer.compute("k", null)
        assertThat(k).startsWith("k:")
    }

    @Test
    fun `키는 promptKey 접두사 포함`() {
        val k = keyer.compute("joseph.s4", mapOf("choice" to "reveal"))
        assertThat(k).startsWith("joseph.s4:")
    }
}
