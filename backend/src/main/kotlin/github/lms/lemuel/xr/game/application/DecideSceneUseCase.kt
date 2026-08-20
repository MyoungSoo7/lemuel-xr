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
    /** pick_one 의 `converges_to` 집행 — 재고 선택은 씬을 넘기지 않는다. */
    private val convergences: SceneConvergenceResolver,
    /** R4 동의 카드의 `skip_alternative_scene_id` · `declined_route` 집행. */
    private val skips: SceneSkipResolver,
) {

    @Transactional
    fun execute(userId: UUID, sessionId: UUID, character: Character, input: Input): Result {
        val session = loadLiveSession(userId, sessionId, character, input)

        val scenario = loader.forCharacter(character)
        val currentScene = scenario.scene(input.sceneId)

        persistDecision(sessionId, currentScene, input)
        recordProgress(session, input)
        sessions.save(session)

        // `converges_to` 선택은 *재고* 다 — 씬을 넘기지 않고 같은 씬에 머문다.
        // 이게 없으면 수렴 구간이 통째로 건너뛰어진다(SceneConvergenceResolver 참조).
        val convergence = convergences.resolve(currentScene, input.decision)
        if (convergence != null) {
            return Result(
                sessionId, input.sceneId,
                input.sceneId, payloads.build(scenario, input.sceneId),
                convergence.reconsiderText
                    ?: responseResolver.resolve(character, currentScene, input.decision, session),
            )
        }

        // R4 동의 카드의 건너뛰기·거절 — `Scene.next` 보다 먼저 본다.
        // 저작된 목적지가 `next` 와 다를 수 있고(룻 Scene 1: skip 3 vs next 2),
        // `next` 로 흘려보내면 건너뛰겠다고 고른 사용자가 건너뛰려던 Scene 으로 들어간다.
        val skip = skips.resolve(scenario, currentScene, input.decision)
        if (skip != null) {
            val responseText = responseResolver.resolve(character, currentScene, input.decision, session)
            return when (skip) {
                is SceneSkipResolver.Skip.ToScene -> Result(
                    sessionId, input.sceneId,
                    skip.sceneId, buildFor(scenario, skip.sceneId, session), responseText,
                )

                is SceneSkipResolver.Skip.AltBlock -> Result(
                    sessionId, input.sceneId,
                    input.sceneId, altBlockPayload(scenario, input.sceneId, skip), responseText,
                )

                SceneSkipResolver.Skip.Closing -> Result(
                    sessionId, input.sceneId,
                    input.sceneId, mapOf("type" to "end"), responseText,
                )
            }
        }

        val next = currentScene.next
        val nextPayload: Map<String, Any?> = if (next == null) {
            mapOf("type" to "end")
        } else {
            buildFor(scenario, next, session)
        }

        // 직전 결정에 대한 응답 텍스트 — realtime LLM vs 정적 lookup 은 ResponseResolver 가 결정.
        val responseText = responseResolver.resolve(character, currentScene, input.decision, session)

        return Result(
            sessionId, input.sceneId,
            next ?: input.sceneId, nextPayload, responseText,
        )
    }

    /** 세션 로드 + 소유권/상태/캐릭터/모드 검증. */
    private fun loadLiveSession(userId: UUID, sessionId: UUID, character: Character, input: Input): GameSession {
        val session = sessions.findById(sessionId)
            .orElseThrow { AppException(ErrorCode.E_SESSION_NOT_FOUND) }
        requireOwner(session, userId)
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

    /**
     * 목적지 Scene 의 payload — **앞선 씬에서 고른 건너뛰기를 여기까지 들고 온다.**
     *
     * 동의 카드 하나가 여러 씬을 덮으므로(룻 중간 카드 = Scene 3·5), 목적지로 점프한 뒤
     * `next` 를 따라 흘러 들어간 씬에서도 그 선택이 살아 있어야 한다. 세션에 기록된 결정이
     * 근거다 — 클라이언트가 매 씬 다시 보내주지 않아도 보호가 이어진다.
     */
    private fun buildFor(scenario: Scenario, sceneId: Int, session: GameSession): Map<String, Any?> {
        val carried = skips.carriedSkip(scenario.scene(sceneId), session.decisions)
            ?: return payloads.build(scenario, sceneId)
        return altBlockPayload(scenario, sceneId, carried)
    }

    /**
     * 마지막 Scene 의 축약 경로 payload — 같은 Scene 을 다시 조립한 뒤 블록의 override 를 덮는다.
     *
     * 블록이 `renders` 로 "이건 남는다" 고 약속한 키가 실제로 없으면 던진다. 축약 경로가
     * 약속한 마감을 못 주면 사용자는 이야기를 잃은 채 끝난다 — 조용히 넘길 실패가 아니다.
     *
     * ─────────────── payload 가 두 겹인 것을 여기서도 본다 ───────────────
     *
     * [ScenarioYamlLoader] 는 표준 9필드만 걷어내고 나머지를 `scene.extras` 로 둔다.
     * yml Scene 이 자기 `extras:` 블록을 갖고 있으면 그것이 **한 겹 더 들어가서**
     * `payload["extras"]` 아래에 앉는다. 룻 Scene 5 가 그 모양이고,
     * `renders: [closing_lines, closing_screen]` 도 `overrides: {captions: []}` 도
     * 전부 그 안쪽 겹에 있다.
     *
     * 루트만 보면 두 가지가 동시에 깨진다 —
     *   · `renders` 검사가 **항상 실패해서** 축약 경로 전체가 E_VALIDATION 으로 죽는다.
     *     (룻 Scene 3 에서 건너뛴 사용자가 Scene 5 에 도착하는 경로가 여기다)
     *   · 설령 통과해도 `captions: []` 가 루트에 얹힐 뿐 `extras.captions` 는 그대로라,
     *     빼기로 한 성문 낭독 자막 2장이 **그대로 화면에 남는다.** 축약을 약속하고
     *     축약하지 않는, 조용한 실패다.
     *
     * 그래서 키가 실제로 사는 겹을 찾아 그 자리에 덮는다. 두 겹 어디에도 없던 키는
     * 블록이 새로 들이는 값이므로 루트에 둔다.
     */
    private fun altBlockPayload(
        scenario: Scenario,
        sceneId: Int,
        skip: SceneSkipResolver.Skip.AltBlock,
    ): Map<String, Any?> {
        val payload = LinkedHashMap(payloads.build(scenario, sceneId))
        val nested = (payload["extras"] as? Map<*, *>)?.let { inner ->
            LinkedHashMap<String, Any?>().apply {
                inner.forEach { (k, v) -> k?.toString()?.let { put(it, v) } }
            }
        }

        val missing = skip.renders.filterNot {
            payload.containsKey(it) || nested?.containsKey(it) == true
        }
        if (missing.isNotEmpty()) {
            throw AppException(
                ErrorCode.E_VALIDATION,
                "Scene $sceneId 축약 블록 '${skip.blockId}' 가 약속한 $missing 이 payload 에 없다",
            )
        }

        skip.overrides.forEach { (key, value) ->
            if (!payload.containsKey(key) && nested?.containsKey(key) == true) {
                nested[key] = value
            } else {
                payload[key] = value
            }
        }
        if (nested != null) payload["extras"] = nested

        payload["conditionalBlockId"] = skip.blockId
        return payload
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
