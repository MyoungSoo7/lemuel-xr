package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameDecisionPort
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameDecision
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class DecideSceneUseCaseTest {

    private val sessions: GameSessionPort = mock()
    private val decisions: GameDecisionPort = mock()
    private val loader: ScenarioYamlLoader = mock()
    private val llm: GenerateLlmResponseUseCase = mock()

    // ResponseResolver 는 실제 협력자(real) — LLM 클라이언트만 mock, 키 추출은 진짜 로직.
    private val uc: DecideSceneUseCase = DecideSceneUseCase(
        sessions, decisions, loader,
        ResponseResolver(llm, DecisionKeyExtractor()),
        // ScenePayloadAssembler 도 실제 협력자 — 위기 토큰 치환까지 통과하는지 함께 본다.
        ScenePayloadAssembler(CrisisTokenResolver { _, _ -> "109" }),
    )

    private fun scene(id: Int, next: Int?, llmFlag: Boolean?, extras: Map<String, Any?>?): Scenario.Scene =
        Scenario.Scene(
            id, "장면$id", "interaction", "pick_one", 60,
            "narr", "ref", llmFlag, next, extras,
        )

    private fun liveSession(id: UUID, character: String?, dimension: String?): GameSession =
        GameSession.reconstitute(
            id, null, null, character, dimension,
            LocalDateTime.now(), null, null, null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    private fun completedSession(id: UUID, character: String?): GameSession =
        GameSession.reconstitute(
            id, null, null, character, null,
            LocalDateTime.now(), LocalDateTime.now(), null, null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    private fun abandonedSession(id: UUID, character: String?): GameSession =
        GameSession.reconstitute(
            id, null, null, character, null,
            LocalDateTime.now(), null, LocalDateTime.now(), null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    // ── happy path: 정적 monologue lookup, next scene payload ──
    @Test
    fun `execute 정적 monologue lookup 후 다음scene payload`() {
        val sid = UUID.randomUUID()
        val monos = mapOf("save_33" to "요셉이 실제로 따른 비율")
        val scenario = Scenario(
            "joseph", "곡식 7년",
            listOf(
                scene(2, 3, false, mapOf("monologues" to monos)),
                scene(3, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", "emotional")))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            sid, Character.JOSEPH,
            DecideSceneUseCase.Input(2, mapOf("value" to "save_33"), mapOf("ms" to 1200), "emotional"),
        )

        assertThat(r.previousScene).isEqualTo(2)
        assertThat(r.currentScene).isEqualTo(3)
        assertThat(r.responseText).isEqualTo("요셉이 실제로 따른 비율")
        assertThat(r.scenePayload).containsEntry("sceneId", 3)

        // decision 영속 검증
        val cap = argumentCaptor<GameDecision>()
        verify(decisions).save(cap.capture())
        assertThat(cap.firstValue.sceneNumber).isEqualTo(2.toShort())
        assertThat(cap.firstValue.sceneName).isEqualTo("장면2")
    }

    @Test
    fun `execute 마지막scene next null 이면 end payload`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "moses", "광야",
            listOf(scene(5, null, false, emptyMap())),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "moses", null)))
        whenever(loader.forCharacter(Character.MOSES)).thenReturn(scenario)

        val r = uc.execute(
            sid, Character.MOSES,
            DecideSceneUseCase.Input(5, mapOf("value" to "go"), null, null),
        )

        assertThat(r.previousScene).isEqualTo(5)
        assertThat(r.currentScene).isEqualTo(5)
        assertThat(r.scenePayload).containsEntry("type", "end")
    }

    @Test
    fun `execute realtime llm 성공`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "joseph", "t",
            listOf(
                scene(4, 5, true, mapOf("reactions" to mapOf("reveal" to "정적"))),
                scene(5, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)
        whenever(llm.execute(any(), any(), any()))
            .thenReturn(GenerateLlmResponseUseCase.Result("LLM 실시간 응답", "openai", "gpt", false))

        val r = uc.execute(
            sid, Character.JOSEPH,
            DecideSceneUseCase.Input(4, mapOf("priority" to "reveal"), null, null),
        )

        assertThat(r.responseText).isEqualTo("LLM 실시간 응답")
        val keyCap = argumentCaptor<String>()
        verify(llm).execute(any(), keyCap.capture(), any())
        assertThat(keyCap.firstValue).isEqualTo("joseph.s4.reaction")
    }

    @Test
    fun `execute realtime llm 실패시 정적 fallback`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "joseph", "t",
            listOf(
                scene(4, 5, true, mapOf("reactions" to mapOf("reveal" to "정적 fallback 텍스트"))),
                scene(5, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)
        whenever(llm.execute(any(), any(), any()))
            .thenThrow(RuntimeException("sidecar down"))

        val r = uc.execute(
            sid, Character.JOSEPH,
            DecideSceneUseCase.Input(4, mapOf("value" to "reveal"), null, null),
        )

        assertThat(r.responseText).isEqualTo("정적 fallback 텍스트")
    }

    // ── 에러 분기 ──
    @Test
    fun `execute 세션없음 E_SESSION_NOT_FOUND`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.empty())
        assertThatThrownBy {
            uc.execute(
                sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }

    @Test
    fun `execute 완료된세션 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(completedSession(sid, "joseph")))
        assertThatThrownBy {
            uc.execute(
                sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute abandoned세션 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(abandonedSession(sid, "joseph")))
        assertThatThrownBy {
            uc.execute(
                sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute character 불일치 E_CHARACTER_UNKNOWN`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "moses", null)))
        assertThatThrownBy {
            uc.execute(
                sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_CHARACTER_UNKNOWN)
    }

    @Test
    fun `execute mode 불일치 E_MODE_MISMATCH`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", "rational")))
        assertThatThrownBy {
            uc.execute(
                sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, "emotional"),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_MODE_MISMATCH)
    }
}
