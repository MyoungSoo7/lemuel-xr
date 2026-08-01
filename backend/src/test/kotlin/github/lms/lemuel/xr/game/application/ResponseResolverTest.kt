package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.game.application.port.out.AiOptOutPort
import github.lms.lemuel.xr.game.domain.Scenario
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * `DecideSceneUseCase` 에서 분리된 정적 응답 매칭(matchResponseText) 로직 커버.
 * 정적 경로만 검증하므로 LLM 클라이언트는 호출되지 않는다(mock), 키 추출은 실제 [DecisionKeyExtractor].
 */
class ResponseResolverTest {

    private val llm: GenerateLlmResponseUseCase = mock()
    private val optOut: AiOptOutPort = mock()
    private val resolver = ResponseResolver(
        llm, DecisionKeyExtractor(), optOut,
        ForbiddenTokenScanner(listOf("믿음이 부족", "빨리 회복")), "안전 대체 문장",
    )

    private fun scene(id: Int, next: Int?, llmFlag: Boolean?, extras: Map<String, Any?>?): Scenario.Scene =
        Scenario.Scene(
            id, "장면$id", "interaction", "pick_one", 60,
            "narr", "ref", llmFlag, next, extras,
        )

    @Test
    fun `matchResponseText priority 우선`() {
        val sc = scene(3, 4, false, mapOf("outcomes" to mapOf("farmer" to "이집트가 살았다")))
        assertThat(resolver.matchResponseText(sc, mapOf("priority" to "farmer")))
            .isEqualTo("이집트가 살았다")
    }

    @Test
    fun `matchResponseText value 필드`() {
        val sc = scene(2, 3, false, mapOf("monologues" to mapOf("save_20" to "일부만")))
        assertThat(resolver.matchResponseText(sc, mapOf("value" to "save_20")))
            .isEqualTo("일부만")
    }

    @Test
    fun `matchResponseText 단일 string wrapper`() {
        val sc = scene(4, 5, false, mapOf("reactions" to mapOf("reveal" to "밝힌다")))
        // {"_": "reveal"} → 값 reveal
        assertThat(resolver.matchResponseText(sc, mapOf("_" to "reveal")))
            .isEqualTo("밝힌다")
    }

    @Test
    fun `matchResponseText 단일 key true 형태`() {
        val sc = scene(2, 3, false, mapOf("monologues" to mapOf("save_33" to "실제 비율")))
        // {"save_33": true} → key save_33
        val d = mutableMapOf<String, Any?>("save_33" to true)
        assertThat(resolver.matchResponseText(sc, d)).isEqualTo("실제 비율")
    }

    @Test
    fun `matchResponseText extras null 이면 null`() {
        val sc = Scenario.Scene(1, "t", "cinematic", null, 60, null, null, false, 2, null)
        assertThat(resolver.matchResponseText(sc, mapOf("value" to "x"))).isNull()
    }

    @Test
    fun `matchResponseText decision null 이면 null`() {
        val sc = scene(2, 3, false, mapOf("monologues" to mapOf("a" to "b")))
        assertThat(resolver.matchResponseText(sc, null)).isNull()
    }

    @Test
    fun `matchResponseText decisionKey 추출불가 null`() {
        val sc = scene(2, 3, false, mapOf("monologues" to mapOf("a" to "b")))
        // priority/value 없고 size>1 → key 추출 불가
        val d = mutableMapOf<String, Any?>("x" to 1, "y" to 2)
        assertThat(resolver.matchResponseText(sc, d)).isNull()
    }

    @Test
    fun `matchResponseText 매칭없으면 null`() {
        val sc = scene(2, 3, false, mapOf("monologues" to mapOf("save_20" to "일부")))
        assertThat(resolver.matchResponseText(sc, mapOf("value" to "nonexistent"))).isNull()
    }
}
