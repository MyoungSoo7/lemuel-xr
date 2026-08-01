package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class StartGameSessionUseCaseTest {

    private val sessions: GameSessionPort = mock()
    private val loader: ScenarioYamlLoader = mock()
    private val payloads =
        ScenePayloadAssembler(CrisisTokenResolver { _, _ -> "109" }, SafetyGateFixtures.sanitizer())
    private val uc = StartGameSessionUseCase(sessions, loader, payloads)

    private fun scene(
        id: Int,
        type: String?,
        interaction: String?,
        next: Int?,
        llm: Boolean?,
        extras: Map<String, Any?>?,
    ): Scenario.Scene =
        Scenario.Scene(
            id, "제목$id", type, interaction, 60,
            "narr$id", "ref$id", llm, next, extras,
        )

    @Test
    fun `execute 는 scene1 payload 와 세션영속`() {
        val scenario = Scenario(
            "joseph", "곡식 7년",
            listOf(
                scene(1, "cinematic", null, 2, null, mapOf("options" to listOf("a", "b"))),
                scene(2, "interaction", "pick_one", null, null, emptyMap()),
            ),
        )
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)
        whenever(sessions.save(any())).thenAnswer { it.getArgument(0) }

        val uid = UUID.randomUUID()
        val result = uc.execute(
            uid, Character.JOSEPH,
            StartGameSessionUseCase.Input(
                "emotional", "quest3",
                mapOf("hmd" to true), 42L,
            ),
        )

        assertThat(result.currentScene).isEqualTo(1)
        assertThat(result.totalScenes).isEqualTo(2)
        assertThat(result.appliedMode).isEqualTo("emotional")
        assertThat(result.scenePayload).containsEntry("sceneId", 1)
            .containsEntry("type", "cinematic")
            .containsEntry("next", 2)
            .containsEntry("options", listOf("a", "b"))
        assertThat(result.sessionId).isNotNull()

        val cap = argumentCaptor<GameSession>()
        verify(sessions).save(cap.capture())
        val saved = cap.firstValue
        assertThat(saved.userId).isEqualTo(uid)
        assertThat(saved.character).isEqualTo("joseph")
        assertThat(saved.chosenDimension).isEqualTo("emotional")
        assertThat(saved.deviceType).isEqualTo("quest3")
        assertThat(saved.capabilities).containsEntry("hmd", true)
        assertThat(saved.triggeredByEmotionLogId).isEqualTo(42L)
    }

    @Test
    fun `ScenePayloadAssembler 는 null 필드 생략하고 realtimeLlm 추가`() {
        val scenario = Scenario(
            "moses", "광야",
            listOf(scene(1, "interaction", null, null, true, null)),
        )
        val p = payloads.build(scenario, 1)
        assertThat(p).containsEntry("sceneId", 1)
            .containsEntry("type", "interaction")
            .containsEntry("realtimeLlm", true)
        assertThat(p).containsKey("narrationId").containsKey("scriptureRef").containsKey("durationSec")
        // interaction 은 null 이므로 생략
        assertThat(p).doesNotContainKey("interaction")
        assertThat(p["next"]).isNull()
    }

    @Test
    fun `ScenePayloadAssembler realtimeLlm false 면 키 없음`() {
        val scenario = Scenario(
            "david", "시편",
            listOf(scene(1, "cinematic", "distribute", 2, false, emptyMap())),
        )
        val p = payloads.build(scenario, 1)
        assertThat(p).doesNotContainKey("realtimeLlm")
        assertThat(p).containsEntry("interaction", "distribute")
    }
}
