package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.domain.Scenario
import org.springframework.stereotype.Component

/**
 * Scene → 클라이언트 payload 로 나가는 *유일한* 지점.
 *
 * /start 와 /decide 가 모두 여기를 통과한다. 시나리오 yml 의 자리표시자
 * ([CrisisTokenResolver.TOKEN] 등) 치환은 반드시 이 한 곳에서만 일어나야 한다 —
 * 경로가 둘이면 언젠가 한쪽이 치환을 빼먹고 원시 토큰이 사용자에게 나간다.
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
