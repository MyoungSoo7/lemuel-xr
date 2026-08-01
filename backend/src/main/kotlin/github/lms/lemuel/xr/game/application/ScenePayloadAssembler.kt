package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.domain.Scenario
import github.lms.lemuel.xr.safety.application.ForbiddenTokenSanitizer
import org.springframework.stereotype.Component

/**
 * Scene → 클라이언트 *scenePayload* 를 만드는 지점. 자리표시자
 * ([CrisisTokenResolver.TOKEN] 등) 치환이 일어나는 곳은 현재 여기뿐이다.
 *
 * /start 와 /decide 의 scenePayload 는 모두 여기를 통과한다.
 *
 * **금지 토큰 게이트도 여기서 걸린다.** `sc.extras` 는 통째로 payload 에 복사되므로
 * `monologues`·`outcomes`·`reactions` 의 원문이 [ResponseResolver] 를 거치지 않고 이 경로로도 나간다.
 * 즉 *같은 문장이 두 출구로 나간다* — responseText 만 막으면 scenePayload 로 그대로 샌다.
 * 그래서 어셈블 결과 전체를 [ForbiddenTokenSanitizer] 로 훑는다 (문자열 leaf 단위).
 *
 * 순서는 위기 토큰 치환 → 금지 토큰 검사다. 사용자가 *실제로 읽게 될 최종 문자열* 을 검사해야
 * 검사와 노출 사이에 틈이 없다.
 *
 * **단, 이것이 시나리오 yml 값이 클라이언트로 나가는 유일한 경로는 아니다.**
 * 아래 경로는 이 어셈블러를 *우회* 한다:
 *
 * - `value_prompt` · `linked_values`
 *   → [CompleteGameSessionUseCase.extractValuePrompt] → `GameController.complete()` 응답
 *
 * 이 경로는 금지 토큰 게이트는 자체적으로 걸지만([CompleteGameSessionUseCase] 참조)
 * **위기 토큰 치환은 여전히 받지 않는다.** 지금 이 키들에는 토큰이 없어서 실제 누출은 없고,
 * 넣는 순간 사용자는 치환되지 않은 원문(`{{crisis_resources.default}}`)을 읽게 된다 —
 * `ScenarioHotlineRatchetTest.ScenePayloadAssembler 를 우회하는 키에는 위기 토큰을 두지 않는다`
 * 가 그 순간 빌드를 깨뜨린다. 위기 안내는 반드시 Scene extras(치환 경로)에 둘 것.
 *
 * 예전에는 `StartGameSessionUseCase.buildScenePayload` companion 함수였다.
 * 치환에 협력자(포트)가 필요해지면서 빈으로 승격했다.
 */
@Component
class ScenePayloadAssembler(
    private val crisisTokens: CrisisTokenResolver,
    private val forbiddenTokens: ForbiddenTokenSanitizer,
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
        return forbiddenTokens.sanitize(crisisTokens.resolve(p), "scenePayload/scene ${sc.id}")
    }
}
