package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StartGameSessionUseCase(
    private val sessions: GameSessionPort,
    private val loader: ScenarioYamlLoader,
    private val payloads: ScenePayloadAssembler,
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

        val payload = payloads.build(scenario, 1)
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
}
