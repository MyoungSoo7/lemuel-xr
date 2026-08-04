package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.domain.Scenario
import org.springframework.stereotype.Component

/**
 * pick_one 선택지의 `converges_to` 를 런타임에서 집행한다.
 *
 * 시나리오 저작에서 `converges_to` 는 **오답 처리가 아니다.** 그 선택은 실패가 아니라
 * *재고(reconsider)* 이고, 서사는 지정된 형제 선택지로 수렴한 뒤에야 다음 Scene 으로
 * 넘어간다(solomon scene3: first_woman·second_woman → sword_test, 왕상 3:24~28).
 *
 * 그런데 엔진은 지금까지 `Scene.next` 만 보고 무조건 다음 씬으로 넘겼다. 그래서 재고 선택도
 * 곧바로 씬을 넘겨 버렸고, "수렴" 으로 설계된 구간이 통째로 건너뛰어졌다. 프론트가
 * `/solomon` 한 페이지에서만 이를 국소적으로 흉내내고 있었다 — 다른 클라이언트(VR 등)나
 * API 직접 호출에는 아무 보호가 없었다는 뜻이다.
 *
 * 여기서 집행하면 저작 의도가 *엔진 계약* 이 된다.
 *
 * 주의 — `options` 의 위치는 인물마다 다르다. 로더가 표준필드만 걷어내므로 yml 의
 * `extras:` 블록은 `scene.extras["extras"]` 로 한 겹 더 들어간다(solomon). joseph 처럼
 * Scene 최상위에 쓴 인물은 `scene.extras["options"]` 다. 양쪽 다 본다.
 */
@Component
class SceneConvergenceResolver(
    private val keyExtractor: DecisionKeyExtractor,
) {

    /**
     * 이번 결정이 *재고* 라면 수렴 정보를, 아니면 null.
     *
     * null 이면 호출자는 평소대로 `Scene.next` 로 진행한다.
     */
    fun resolve(scene: Scenario.Scene, decision: Map<String, Any?>?): Convergence? {
        val chosenId = keyExtractor.extract(decision) ?: return null
        val block = optionBlock(scene) ?: return null
        val options = block["options"] as? List<*> ?: return null

        val chosen = options.filterIsInstance<Map<*, *>>()
            .firstOrNull { it["id"]?.toString() == chosenId }
            ?: return null

        val target = chosen["converges_to"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        // 자기 자신으로의 수렴은 수렴이 아니다 — 무한정 같은 씬에 갇힌다.
        if (target == chosenId) return null

        val reconsiderText = (block["reconsider_texts"] as? Map<*, *>)
            ?.get(chosenId)?.toString()?.takeIf { it.isNotBlank() }

        return Convergence(chosenId, target, reconsiderText)
    }

    /**
     * `options` 를 담고 있는 맵을 찾는다 — Scene 최상위 우선, 없으면 중첩된 `extras` 블록.
     */
    private fun optionBlock(scene: Scenario.Scene): Map<*, *>? {
        val extras = scene.extras ?: return null
        if (extras["options"] is List<*>) return extras
        val nested = extras["extras"] as? Map<*, *> ?: return null
        return if (nested["options"] is List<*>) nested else null
    }

    /**
     * @param chosenId 사용자가 실제로 고른 선택지
     * @param convergesTo 서사가 수렴하는 형제 선택지 id
     * @param reconsiderText 재고 텍스트(저작된 정적 문구). 없으면 null.
     */
    data class Convergence(
        val chosenId: String,
        val convergesTo: String,
        val reconsiderText: String?,
    )
}
