package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.game.application.port.out.AiOptOutPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

/**
 * R5("정적 큐레이션이 기본 경로, LLM 은 명시적 opt-in") 의 런타임 게이트 두 축.
 *
 * - Gap A — `users.ai_opt_out=true` 사용자는 `realtimeLlm=true` Scene 에서도 LLM 이 *호출되지 않는다*.
 *   반환 텍스트만 보면 정적 fallback 과 우연히 같을 수 있으므로 `verify(llm, never())` 로 단언한다.
 * - Gap B — yml 정적 큐레이션 텍스트도 [ForbiddenTokenScanner] 를 통과한다.
 *   LLM 경로만 막던 게이트를 기본 경로에도 채운다. 정적 텍스트는 재생성이 불가능하므로
 *   [GenerateLlmResponseUseCase] 와 달리 재시도는 없고 곧장 `safety.forbidden-tokens.fallback-text` 다.
 */
class ResponseResolverSafetyTest {

    private val llm: GenerateLlmResponseUseCase = mock()
    private val optOut: AiOptOutPort = mock()

    /** 실제 스캐너 — application.yml 목록 중 두 개를 실은 것과 동등한 구성. */
    private val scanner = ForbiddenTokenScanner(listOf("믿음이 부족", "빨리 회복"))

    private val fallbackText = "지금은 어떤 말도 보태지 않겠습니다. 여기 이대로 머물러도 괜찮습니다."

    private val resolver = ResponseResolver(llm, DecisionKeyExtractor(), optOut, scanner, fallbackText)

    private fun scene(id: Int, llmFlag: Boolean?, extras: Map<String, Any?>?): Scenario.Scene =
        Scenario.Scene(id, "장면$id", "interaction", "pick_one", 60, "narr", "ref", llmFlag, id + 1, extras)

    private fun session(userId: UUID?): GameSession =
        GameSession.reconstitute(
            UUID.randomUUID(), userId, null, "joseph", "emotional",
            LocalDateTime.now(), null, null, null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    // ── Gap A — opt-out 사용자에게는 LLM 호출 자체가 없어야 한다 ──────────────────

    @Test
    fun `opt-out 사용자는 realtimeLlm Scene 에서도 LLM 이 호출되지 않고 정적 텍스트를 받는다`() {
        val userId = UUID.randomUUID()
        whenever(optOut.isOptedOut(userId)).thenReturn(true)
        val sc = scene(4, true, mapOf("reactions" to mapOf("reveal" to "요셉은 형들 앞에서 울었다")))

        val text = resolver.resolve(Character.JOSEPH, sc, mapOf("_" to "reveal"), session(userId))

        // 예외를 잡아 fallback 한 것이 아니라 *애초에 부르지 않은* 것이어야 한다.
        verify(llm, never()).execute(any(), any(), any())
        assertThat(text).isEqualTo("요셉은 형들 앞에서 울었다")
    }

    @Test
    fun `opt-out 사용자는 정적 텍스트가 없으면 null 을 받는다 - 그래도 LLM 은 부르지 않는다`() {
        val userId = UUID.randomUUID()
        whenever(optOut.isOptedOut(userId)).thenReturn(true)
        val sc = scene(4, true, null)

        val text = resolver.resolve(Character.JOSEPH, sc, mapOf("_" to "reveal"), session(userId))

        verify(llm, never()).execute(any(), any(), any())
        assertThat(text).isNull()
    }

    @Test
    fun `opt-out 하지 않은 사용자는 종전대로 realtime LLM 을 받는다`() {
        val userId = UUID.randomUUID()
        whenever(optOut.isOptedOut(userId)).thenReturn(false)
        whenever(llm.execute(any(), any(), any()))
            .thenReturn(GenerateLlmResponseUseCase.Result("실시간 생성 응답", "p", "m", false))
        val sc = scene(4, true, mapOf("reactions" to mapOf("reveal" to "정적 텍스트")))

        val text = resolver.resolve(Character.JOSEPH, sc, mapOf("_" to "reveal"), session(userId))

        verify(llm).execute(any(), any(), any())
        assertThat(text).isEqualTo("실시간 생성 응답")
    }

    @Test
    fun `정적 Scene 은 opt-out 을 조회할 필요조차 없다`() {
        val sc = scene(2, false, mapOf("monologues" to mapOf("save_33" to "실제 비율")))

        val text = resolver.resolve(Character.JOSEPH, sc, mapOf("value" to "save_33"), session(UUID.randomUUID()))

        verify(llm, never()).execute(any(), any(), any())
        assertThat(text).isEqualTo("실제 비율")
    }

    // ── Gap B — 정적 큐레이션 텍스트도 금지 토큰 게이트를 통과해야 한다 ──────────────

    @Test
    fun `정적 monologue 의 금지 토큰은 fallback 으로 대체된다`() {
        val sc = scene(4, false, mapOf("monologues" to mapOf("give_up" to "믿음이 부족해서 그런 일을 겪은 겁니다")))

        assertThat(resolver.matchResponseText(sc, mapOf("value" to "give_up"))).isEqualTo(fallbackText)
    }

    @Test
    fun `공백이 늘어난 금지 토큰도 정적 경로에서 잡힌다`() {
        // 스캐너의 공백 정규화가 정적 경로에도 적용되는지 — 저작 yml 은 줄바꿈이 섞이기 쉽다.
        val sc = scene(4, false, mapOf("outcomes" to mapOf("hurry" to "이제 그만하고  빨리   회복 하세요")))

        assertThat(resolver.matchResponseText(sc, mapOf("priority" to "hurry"))).isEqualTo(fallbackText)
    }

    @Test
    fun `깨끗한 정적 텍스트는 그대로 통과한다`() {
        val sc = scene(4, false, mapOf("reactions" to mapOf("reveal" to "요셉은 형들 앞에서 울었다")))

        assertThat(resolver.matchResponseText(sc, mapOf("_" to "reveal"))).isEqualTo("요셉은 형들 앞에서 울었다")
    }

    @Test
    fun `realtime LLM 실패 후 정적 fallback 도 게이트를 통과한다`() {
        val userId = UUID.randomUUID()
        whenever(optOut.isOptedOut(userId)).thenReturn(false)
        whenever(llm.execute(any(), any(), any())).thenThrow(RuntimeException("사이드카 down"))
        val sc = scene(4, true, mapOf("reactions" to mapOf("reveal" to "믿음이 부족해서 그렇습니다")))

        val text = resolver.resolve(Character.JOSEPH, sc, mapOf("_" to "reveal"), session(userId))

        assertThat(text).isEqualTo(fallbackText)
    }

    @Test
    fun `opt-out 사용자의 정적 텍스트도 게이트를 통과한다`() {
        val userId = UUID.randomUUID()
        whenever(optOut.isOptedOut(userId)).thenReturn(true)
        val sc = scene(4, true, mapOf("reactions" to mapOf("reveal" to "믿음이 부족해서 그렇습니다")))

        val text = resolver.resolve(Character.JOSEPH, sc, mapOf("_" to "reveal"), session(userId))

        verify(llm, never()).execute(any(), any(), any())
        assertThat(text).isEqualTo(fallbackText)
    }
}
