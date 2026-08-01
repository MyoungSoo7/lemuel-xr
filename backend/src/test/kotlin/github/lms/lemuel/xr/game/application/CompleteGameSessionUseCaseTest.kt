package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class CompleteGameSessionUseCaseTest {

    private val sessions: GameSessionPort = mock()
    private val scenarios: ScenarioYamlLoader = mock()
    // 이 스위트는 완료·valuePrompt 동작만 검증한다. 종료 메시지 위기 스캔은
    // CompleteGameSessionCrisisScanTest 가 따로 다루므로 여기서는 매칭되지 않는
    // 패턴을 주입해 기존 검증에 영향이 없게 한다.
    private val uc = CompleteGameSessionUseCase(
        sessions, scenarios,
        CrisisKeywordScanner("(?<suicideIntent>\\bZZZ_NO_MATCH\\b)"),
        mock(),
        SafetyGateFixtures.sanitizer(),
    )

    private fun live(id: UUID, character: String?, started: LocalDateTime?): GameSession =
        GameSession.reconstitute(
            id, null, null, character, null,
            started, null, null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun completed(id: UUID, character: String?): GameSession =
        GameSession.reconstitute(
            id, null, null, character, null,
            LocalDateTime.now(), LocalDateTime.now(), null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun abandoned(id: UUID, character: String?): GameSession =
        GameSession.reconstitute(
            id, null, null, character, null,
            LocalDateTime.now(), null, LocalDateTime.now(), null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun outro(next: Int?, extras: Map<String, Any?>?): Scenario.Scene =
        Scenario.Scene(5, "결말", "outro", null, null, null, "gen-45:5", false, next, extras)

    @Test
    fun `execute 는 완료시각 duration valuePrompt 반환`() {
        val sid = UUID.randomUUID()
        val started = LocalDateTime.now().minusSeconds(120)
        whenever(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "joseph", started)))
        val scenario = Scenario(
            "joseph", "곡식 7년",
            listOf(
                outro(
                    null,
                    mapOf(
                        "linked_values" to listOf(1, 2, 7),
                        "value_prompt" to "오늘 3줄 일기",
                    ),
                ),
            ),
        )
        whenever(scenarios.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(sid, "farmer_first", "수고했어요")

        assertThat(r.completedAt).isNotNull()
        assertThat(r.durationSeconds).isGreaterThanOrEqualTo(119)
        assertThat(r.valuePrompt).isNotNull()
        assertThat(r.valuePrompt!!.suggestedValueIds).containsExactly(1, 2, 7)
        assertThat(r.valuePrompt!!.message).isEqualTo("오늘 3줄 일기")
        assertThat(r.valuePrompt!!.linkedCharacter).isEqualTo("joseph")
    }

    @Test
    fun `valuePrompt 메시지도 금지 토큰 게이트를 통과한다`() {
        // `value_prompt` 는 ScenePayloadAssembler 를 우회해 곧장 /complete 응답으로 나가는
        // 사용자 노출 문장이다. 우회 목록에 있다는 사실은 예전부터 적혀 있었지만 위기 토큰 관점뿐이었고,
        // 금지 토큰은 아무도 안 봤다. 세션의 감정적 정점에서 나가는 문장이라 특히 위험하다.
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "joseph", LocalDateTime.now())))
        whenever(scenarios.forCharacter(Character.JOSEPH)).thenReturn(
            Scenario(
                "joseph", "곡식 7년",
                listOf(
                    outro(
                        null,
                        mapOf(
                            "linked_values" to listOf(1),
                            "value_prompt" to "믿음이 부족해서 그런 일을 겪은 겁니다",
                        ),
                    ),
                ),
            ),
        )

        val r = uc.execute(sid, "out", null)

        assertThat(r.valuePrompt!!.message).isEqualTo(SafetyGateFixtures.FALLBACK_TEXT)
        // 게이트는 문장만 바꾼다 — 구조는 그대로다.
        assertThat(r.valuePrompt!!.suggestedValueIds).containsExactly(1)
        assertThat(r.valuePrompt!!.linkedCharacter).isEqualTo("joseph")
    }

    @Test
    fun `execute outro가 next null 없으면 마지막scene fallback`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "moses", LocalDateTime.now())))
        // 모든 scene next != null → 마지막 scene fallback 경로
        val s1 = Scenario.Scene(1, "a", "cinematic", null, null, null, null, false, 2, emptyMap())
        val s2 = Scenario.Scene(
            2, "b", "outro", null, null, null, null, false, 3,
            mapOf("value_prompt" to "광야의 실천"),
        )
        whenever(scenarios.forCharacter(Character.MOSES)).thenReturn(Scenario("moses", "t", listOf(s1, s2)))

        val r = uc.execute(sid, "out", null)
        assertThat(r.valuePrompt).isNotNull()
        assertThat(r.valuePrompt!!.message).isEqualTo("광야의 실천")
        assertThat(r.valuePrompt!!.suggestedValueIds).isEmpty()
    }

    @Test
    fun `execute linked_values도 prompt도 없으면 valuePrompt null`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "david", LocalDateTime.now())))
        whenever(scenarios.forCharacter(Character.DAVID)).thenReturn(
            Scenario("david", "t", listOf(outro(null, mapOf("other" to "x")))),
        )

        val r = uc.execute(sid, "out", null)
        assertThat(r.valuePrompt).isNull()
    }

    @Test
    fun `execute extras null 이면 valuePrompt null`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "david", LocalDateTime.now())))
        whenever(scenarios.forCharacter(Character.DAVID)).thenReturn(
            Scenario("david", "t", listOf(outro(null, null))),
        )

        val r = uc.execute(sid, "out", null)
        assertThat(r.valuePrompt).isNull()
    }

    @Test
    fun `execute 알수없는 character면 valuePrompt null graceful`() {
        val sid = UUID.randomUUID()
        val e = live(sid, "unknown_char", LocalDateTime.now())
        whenever(sessions.findById(sid)).thenReturn(Optional.of(e))
        // Character.from 이 예외 → catch → null
        val r = uc.execute(sid, "out", null)
        assertThat(r.valuePrompt).isNull()
    }

    @Test
    fun `execute startedAt null 이면 duration null`() {
        val sid = UUID.randomUUID()
        val e = live(sid, "david", null)
        whenever(sessions.findById(sid)).thenReturn(Optional.of(e))
        whenever(scenarios.forCharacter(Character.DAVID)).thenReturn(
            Scenario("david", "t", listOf(outro(null, emptyMap()))),
        )
        val r = uc.execute(sid, "out", null)
        assertThat(r.durationSeconds).isNull()
    }

    @Test
    fun `execute 세션없음 E_SESSION_NOT_FOUND`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.empty())
        assertThatThrownBy { uc.execute(sid, "out", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }

    @Test
    fun `execute 이미완료 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(completed(sid, "joseph")))
        assertThatThrownBy { uc.execute(sid, "out", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute abandoned E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(abandoned(sid, "joseph")))
        assertThatThrownBy { uc.execute(sid, "out", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }
}
