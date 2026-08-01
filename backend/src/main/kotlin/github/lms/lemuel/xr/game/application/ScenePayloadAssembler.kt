package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.domain.Scenario
import org.springframework.stereotype.Component

/**
 * Scene → 클라이언트 *scenePayload* 를 만드는 지점. 자리표시자
 * ([CrisisTokenResolver.TOKEN] 등) 치환이 일어나는 곳은 현재 여기뿐이다.
 *
 * /start 와 /decide 의 scenePayload 는 모두 여기를 통과한다.
 *
 * **단, 이것이 시나리오 yml 값이 클라이언트로 나가는 유일한 경로는 아니다.**
 * 아래 두 경로는 이 어셈블러를 *우회* 하므로 치환이 적용되지 않는다:
 *
 * 1. `value_prompt` · `linked_values`
 *    → [CompleteGameSessionUseCase.extractValuePrompt] → `GameController.complete()` 응답
 * 2. Scene extras 의 `monologues` · `outcomes` · `reactions`
 *    → [ResponseResolver.matchResponseText] → [DecideSceneUseCase] 의 `responseText`
 *
 * 지금 이 키들에는 토큰이 없어서 실제 누출은 없다. 넣는 순간 사용자는 치환되지 않은
 * 원문(`{{crisis_resources.default}}`)을 읽게 된다 —
 * `ScenarioHotlineRatchetTest.ScenePayloadAssembler 를 우회하는 키에는 위기 토큰을 두지 않는다`
 * 가 그 순간 빌드를 깨뜨린다. 위기 안내는 반드시 Scene extras(치환 경로)에 둘 것.
 *
 * 예전에는 `StartGameSessionUseCase.buildScenePayload` companion 함수였다.
 * 치환에 협력자(포트)가 필요해지면서 빈으로 승격했다.
 */
@Component
class ScenePayloadAssembler(
    private val crisisTokens: CrisisTokenResolver,
) {

    fun build(scenario: Scenario, sceneId: Int): Map<String, Any?> {
        val sc = scenario.scene(sceneId)
        val p = LinkedHashMap<String, Any?>()
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
        return crisisTokens.resolve(p)
    }
}
