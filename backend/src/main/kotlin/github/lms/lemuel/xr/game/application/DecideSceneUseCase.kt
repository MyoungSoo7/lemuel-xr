package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameDecisionPort
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameDecision
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DecideSceneUseCase(
    private val sessions: GameSessionPort,
    private val decisions: GameDecisionPort,
    private val loader: ScenarioYamlLoader,
    /** Phase 2-B — Scene.realtimeLlm=true 일 때 realtime LLM, 없으면 정적 fallback 을 골라주는 전략. */
    private val responseResolver: ResponseResolver,
    private val payloads: ScenePayloadAssembler,
) {

    @Transactional
    fun execute(sessionId: UUID, character: Character, input: Input): Result {
        val session = loadLiveSession(sessionId, character, input)

        val scenario = loader.forCharacter(character)
        val currentScene = scenario.scene(input.sceneId)

        persistDecision(sessionId, currentScene, input)
        recordProgress(session, input)
        sessions.save(session)

        val next = currentScene.next
        val nextPayload: Map<String, Any?> = if (next == null) {
            mapOf("type" to "end")
        } else {
            payloads.build(scenario, next)
        }

        // 직전 결정에 대한 응답 텍스트 — realtime LLM vs 정적 lookup 은 ResponseResolver 가 결정.
        val responseText = responseResolver.resolve(character, currentScene, input.decision, session)

        return Result(
            sessionId, input.sceneId,
            next ?: input.sceneId, nextPayload, responseText,
        )
    }

    /** 세션 로드 + 상태/캐릭터/모드 검증. */
    private fun loadLiveSession(sessionId: UUID, character: Character, input: Input): GameSession {
        val session = sessions.findById(sessionId)
            .orElseThrow { AppException(ErrorCode.E_SESSION_NOT_FOUND) }
        if (session.isTerminated()) {
            throw AppException(ErrorCode.E_SESSION_INVALID)
        }
        if (!session.character.equals(character.dbValue, ignoreCase = true)) {
            throw AppException(ErrorCode.E_CHARACTER_UNKNOWN)
        }
        if (input.mode != null && session.chosenDimension != null &&
            !input.mode.equals(session.chosenDimension, ignoreCase = true)
        ) {
            throw AppException(ErrorCode.E_MODE_MISMATCH)
        }
        return session
    }

    /** 결정 영속. */
    private fun persistDecision(sessionId: UUID, currentScene: Scenario.Scene, input: Input) {
        decisions.save(
            GameDecision.record(
                sessionId, input.sceneId, currentScene.title,
                input.decision, input.interactionMeta,
            ),
        )
    }

    /** 세션 decisions JSONB + 진행 Scene 카운트 업데이트. */
    private fun recordProgress(session: GameSession, input: Input) {
        session.recordDecision(input.sceneId, input.decision)
        session.advanceSceneCount(input.sceneId)
    }

    data class Input(
        val sceneId: Int,
        val decision: Map<String, Any?>?,
        val interactionMeta: Map<String, Any?>?,
        val mode: String?,
    )

    data class Result(
        val sessionId: UUID,
        val previousScene: Int,
        val currentScene: Int,
        val scenePayload: Map<String, Any?>,
        val responseText: String?,
    )
}
