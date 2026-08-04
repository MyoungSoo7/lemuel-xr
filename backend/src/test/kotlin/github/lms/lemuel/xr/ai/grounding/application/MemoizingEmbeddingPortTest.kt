package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 메모이즈가 **비용만** 줄이고 **의미는** 바꾸지 않는지 확인한다.
 * 여기가 틀리면 스윕 전체가 엉뚱한 벡터로 채점된다.
 */
class MemoizingEmbeddingPortTest {

    /** 위임 호출을 그대로 기록하는 스파이 — 무엇이 실제로 API 를 탔는지가 이 테스트의 관심사다. */
    private class Spy(private val fail: Boolean = false, private val truncate: Boolean = false) : EmbeddingPort {
        val calls = mutableListOf<List<String>>()
        override fun embed(texts: List<String>): List<FloatArray> {
            calls += texts
            if (fail) throw RuntimeException("backend down")
            val out = texts.map { floatArrayOf(it.length.toFloat()) }
            return if (truncate) out.drop(1) else out
        }
    }

    @Test
    fun `같은 텍스트는 두 번 임베딩하지 않는다`() {
        val spy = Spy()
        val port = MemoizingEmbeddingPort(spy)

        port.embed(listOf("가", "나"))
        port.embed(listOf("나", "다"))

        // 2회차에 "나" 는 캐시에서 오고 "다" 만 위임된다.
        assertThat(spy.calls).containsExactly(listOf("가", "나"), listOf("다"))
        assertThat(port.embeddedTexts).isEqualTo(3)
        assertThat(port.cachedTexts).isEqualTo(3)
    }

    @Test
    fun `한 요청 안의 중복도 한 번만 태운다`() {
        val spy = Spy()
        val port = MemoizingEmbeddingPort(spy)

        val result = port.embed(listOf("같음", "같음", "다름"))

        assertThat(spy.calls).containsExactly(listOf("같음", "다름"))
        // 중복을 제거해 보냈더라도 **요청 순서와 개수 그대로** 돌려줘야 한다.
        assertThat(result).hasSize(3)
        assertThat(result[0]).isSameAs(result[1])
        assertThat(result[2]).isNotSameAs(result[0])
    }

    @Test
    fun `캐시 적중이어도 요청 순서대로 돌려준다`() {
        val port = MemoizingEmbeddingPort(Spy())
        port.embed(listOf("길다길다", "짧다"))

        // 순서가 어긋나면 문장↔벡터 짝이 뒤바뀌어 유사도가 조용히 오염된다.
        val result = port.embed(listOf("짧다", "길다길다", "짧다"))
        assertThat(result.map { it[0] }).containsExactly(2f, 4f, 2f)
    }

    @Test
    fun `응답 개수가 요청과 다르면 즉시 실패한다`() {
        // 조용히 짝이 밀리면 전 픽스처의 유사도가 틀린 채 그럴듯한 숫자로 보고된다.
        assertThatThrownBy { MemoizingEmbeddingPort(Spy(truncate = true)).embed(listOf("가", "나")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("개수 불일치")
    }

    @Test
    fun `위임이 실패하면 캐시를 오염시키지 않고 그대로 전파한다`() {
        val port = MemoizingEmbeddingPort(Spy(fail = true))

        assertThatThrownBy { port.embed(listOf("가")) }.hasMessage("backend down")
        assertThat(port.embeddedTexts).isZero()
        assertThat(port.cachedTexts).isZero()
    }
}
