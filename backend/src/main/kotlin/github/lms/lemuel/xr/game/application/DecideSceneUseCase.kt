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
                    input.sceneId, closingPayload(scenario), responseText,
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

    /**
     * 동의 카드를 **거절한** 사용자의 종결 payload — 저작된 `closing_screen` 을 실어 보낸다.
     *
     * ─────────────────────────── 왜 고쳤나 ───────────────────────────
     *
     * 여기는 `mapOf("type" to "end")` 만 내보내고 있었다. 그런데 룻 Scene 5 의
     * `extras.closing_screen` 은 `reached_by` 에 **`consent_declined_sentinel` 을 명시**하고
     * 있다 — 저작은 거절한 사용자도 이 화면에 착지시키기로 했고, 그 옆에 이렇게 적어 두었다:
     * 「세 경로가 모두 이 블록에 착지한다 — **덜 본 사람에게 덜한 결말을 주지 않는다.**」
     * 코드는 그 약속을 지키지 않았다. 거절한 사람만 빈 `end` 를 받고 끝났다.
     *
     * 이게 자막 하나 빠지는 문제가 아닌 이유는 그 블록의 `ui_overlays` 다 —
     * `[suffering_disclaimer, crisis_reminder, exit_button]`. 즉 **거절한 사용자에게만**
     * 위기 안내도, 나가기 버튼도, 면책도 안 갔다. 카드를 거절한다는 것은 그 내용을 보지
     * 않겠다는 뜻이고, 그런 선택을 한 사용자야말로 이 세 가지가 필요한 쪽이다.
     * 리포 전체에서 `declined_route` 는 룻 하나뿐이라 범위는 좁지만 성격은 안전이다.
     *
     * ─────────────────────────── 규약 ───────────────────────────
     *
     * `type: "end"` 는 그대로 둔다 — 클라이언트가 "미션이 끝났다" 를 읽는 자리다.
     * 그 옆에 종결 화면과 위기 안내 문자열을 **저작된 이름 그대로**(`closing_screen` ·
     * `crisis_reminder`) 싣는다. 프론트가 이미 그 이름으로 읽고 있어서다
     * (`/ruth` 의 `field()` — `payload.extras[key] ?? payload[key]`). 여기서 camelCase 로
     * 바꿔 실으면 백엔드는 고친 것처럼 보이고 화면은 그대로 비어 있다.
     *
     * ─────────────── 통째로 싣지 않고 **골라** 싣는 이유 ───────────────
     *
     * 종결 화면은 Scene 5 안에 산다. 그런데 이 경로로 오는 사용자는 중간 동의 카드를
     * 거절한 사람이고, 그 카드의 `covers_scenes` 는 **[3, 5]** 다 — Scene 5 의 내용도
     * 거절한 것이다. Scene 5 payload 를 통째로 넘기면 성문 낭독 자막이 딸려 가서, 안 보겠다고
     * 고른 사람에게 그 내용을 보여 주게 된다. 그래서 뺄 것을 지우는 대신 **실을 것만 고른다** —
     * 새 키가 저작되어도 기본값이 "안 실림" 이어야 이 실수가 재발하지 않는다.
     *
     * 고르기 전에 [ScenePayloadAssembler] 를 한 번 통과시킨다. `crisis_reminder` 는
     * `{{crisis_resources.default}}` 토큰이라 치환 없이 실으면 사용자가 중괄호를 본다.
     *
     * `closing_screen` 이 없는데 `declined_route: closing` 을 선언한 시나리오는 던진다.
     * 조용히 `end` 로 흘리면 지금 고친 이 결함이 새 인물에서 그대로 재발하고, 그때도
     * 아무도 모른다 — 바로 그래서 이 버그가 오래 살았다. 축약 경로가 약속한 키를 못 찾을 때
     * [altBlockPayload] 가 던지는 것과 같은 이유다. 배포 전에 잡으라고
     * `DeclinedRouteClosingScreenTest` 가 실제 yml 에 대고 같은 것을 정적으로도 잰다.
     *
     * ⚠️ 미해소 — 마감 한 줄(`closing_lines`)을 거절 경로에도 줄지는 **저작이 답하지 않았다.**
     * 건너뛰기 경로에는 `renders: [closing_lines, closing_screen]` 로 명시돼 있지만
     * 거절 경로에는 `closing_screen.reached_by` 센티널뿐이다. 여기서 지어내지 않고 뺐다.
     */
    private fun closingPayload(scenario: Scenario): Map<String, Any?> {
        val holder = scenario.scenes.firstOrNull { closingScreenOf(it) != null }
            ?: throw AppException(
                ErrorCode.E_VALIDATION,
                "declined_route: closing 을 선언했는데 어느 Scene 에도 extras.$CLOSING_SCREEN 이 없다 — " +
                    "거절한 사용자가 받을 화면이 저작되지 않았다",
            )

        val resolved = payloads.build(scenario, holder.id)
        val roots: List<Map<*, *>> = listOfNotNull(resolved, resolved["extras"] as? Map<*, *>)

        val out = LinkedHashMap<String, Any?>()
        out["type"] = "end"
        CLOSING_KEYS.forEach { key ->
            roots.firstNotNullOfOrNull { it[key] }?.let { out[key] = it }
        }
        return out
    }

    /**
     * Scene 에서 종결 화면 블록을 꺼낸다 — **두 겹을 다 본다.**
     *
     * 로더가 표준필드만 걷어내므로, yml Scene 이 자기 `extras:` 블록을 명시적으로 쓰면
     * 그 안의 키들은 `scene.extras["extras"]` 로 **한 겹 더 들어간다.** 룻 Scene 5 가
     * 정확히 그 모양이라(`scenes[4].extras.closing_screen`), 최상위만 보면 못 찾는다.
     * [SceneSkipResolver] · [SceneConvergenceResolver] 가 같은 이유로 같은 짓을 한다.
     *
     * 처음 이 고침을 쓸 때 최상위만 봤고, 픽스처가 최상위에 두는 바람에 단위 테스트는
     * 초록이었다. 실제 ruth.yml 에 대고 재는 `DeclinedRouteClosingScreenTest` 가 그걸
     * 잡았다 — 정적 검사를 함께 둔 이유가 이것이다.
     */
    private fun closingScreenOf(scene: Scenario.Scene): Map<*, *>? {
        val roots = listOfNotNull(scene.extras, scene.extras?.get("extras") as? Map<*, *>)
        return roots.firstNotNullOfOrNull { it[CLOSING_SCREEN] as? Map<*, *> }
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

    private companion object {
        /** 저작이 종결 화면을 두는 자리 — `scenes[].extras.closing_screen`. */
        const val CLOSING_SCREEN = "closing_screen"

        /**
         * 거절한 사용자에게 실어 보낼 키 — **허용목록**이다.
         *
         * 종결 화면이 사는 Scene 은 그 사용자가 거절한 내용도 함께 갖고 있다. 그래서
         * "뺄 것" 이 아니라 "실을 것" 을 적는다. 새 키가 저작되면 기본값이 *안 실림* 이고,
         * 실어야 한다면 그 결정이 이 줄에 남는다.
         *
         * `crisis_reminder` 는 화면의 `ui_overlays` 가 요구하는 문자열이다 — 빠지면
         * 화면은 오되 위기 안내만 조용히 사라진다.
         */
        val CLOSING_KEYS = listOf(CLOSING_SCREEN, "crisis_reminder")
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
