package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StartGameSessionUseCase(
    private val sessions: GameSessionPort,
    private val loader: ScenarioYamlLoader,
) {

    @Transactional
    fun execute(userId: UUID?, character: Character, input: Input): Result {
        val scenario = loader.forCharacter(character)

        val session = GameSession.start(
            userId,
            character.dbValue,
            input.mode,
            input.deviceType,
            input.capabilities,
            input.linkedEmotionLogId,
        )
        val saved = sessions.save(session)

        val payload = buildScenePayload(scenario, 1)
        return Result(saved.id!!, 1, scenario.totalScenes(), input.mode, payload)
    }

    data class Input(
        val mode: String?,
        val deviceType: String?,
        val capabilities: Map<String, Any?>?,
        val linkedEmotionLogId: Long?,
    )

    data class Result(
        val sessionId: UUID,
        val currentScene: Int,
        val totalScenes: Int,
        val appliedMode: String?,
        val scenePayload: Map<String, Any?>,
    )

    companion object {
        fun buildScenePayload(scenario: Scenario, sceneId: Int): Map<String, Any?> {
            val sc = scenario.scene(sceneId)
            val p = HashMap<String, Any?>()
            p["sceneId"] = sc.id
            p["title"] = sc.title
            p["type"] = sc.type
            sc.interaction?.let { p["interaction"] = it }
            sc.durationSec?.let { p["durationSec"] = it }
            sc.narrationId?.let { p["narrationId"] = it }
            sc.scriptureRef?.let { p["scriptureRef"] = it }
            if (sc.realtimeLlm == true) p["realtimeLlm"] = true
            p["next"] = sc.next
            sc.extras?.let { p.putAll(it) }
            return p
        }
    }
}
