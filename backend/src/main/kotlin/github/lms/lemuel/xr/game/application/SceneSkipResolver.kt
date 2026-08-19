package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.domain.Scenario
import org.springframework.stereotype.Component

/**
 * R4 동의 카드의 **건너뛰기·거절 목적지** 를 런타임에서 집행한다.
 *
 * ───────────────────────── 왜 이게 필요했나 ─────────────────────────
 *
 * 시나리오 저작에서 동의 카드는 두 개의 문을 준다 — `skip`(이 장면만 건너뛰고 계속) 과
 * `decline`(카드 자체를 거절하고 종결). 목적지는 yml 에 `skip_alternative_scene_id` ·
 * `declined_route` 로 적혀 있다.
 *
 * 그런데 **엔진은 이 두 키를 한 번도 읽지 않았다.** `DecideSceneUseCase` 는 결정을 기록한 뒤
 * 무조건 `Scene.next` 를 따라갔다. 요셉·예수·솔로몬·엘리야·욥은 `skip == next` 라서
 * *우연히* 맞아떨어졌을 뿐이다. 룻 Scene 1 은 진입 카드가 Scene 1·2 를 함께 덮으므로
 * 스킵 목적지가 3인데 `next` 는 2다 — 그대로 열었다면 **사별 서사를 건너뛰겠다고 고른
 * 사용자가 그 카드가 덮기로 한 바로 그 Scene 2 로 들어갔다.**
 *
 * 안전 통제는 선언만으로는 통제가 아니다. 여기서 집행해야 저작 의도가 엔진 계약이 된다.
 *
 * ───────────────────────── 목적지 세 형태 ─────────────────────────
 *
 * `ruth.yml` 머리 주석의 계약 그대로다 —
 * `consent_card_id` → `skip_alternative_scene_id` → (문자열이면) `conditional_blocks[].id`.
 *
 * 1. **정수** → 그 Scene 으로 점프한다. (룻 1→3, 4→5 / 요셉 4→5 …)
 * 2. **문자열** → 다음 Scene 이 없는 마지막 Scene 의 축약 경로다. 같은 Scene 에 머물되
 *    그 Scene 의 `conditional_blocks` 에서 같은 id 를 찾아 **블록이 선언한 override 를
 *    payload 에 덮는다.** 룻 Scene 5 의 `ruth_scene5_alt_short` 는 `captions: []` 을
 *    선언하므로 성문 낭독 자막 2장이 실제로 빠지고, `renders: [closing_lines,
 *    closing_screen]` 이 약속한 마감 한 줄과 종결 화면은 그대로 남는다.
 * 3. **`declined_route: closing`** → 종결. 스킵과 거절은 다른 동작이다(룻 Scene 3 주석).
 *
 * ───────────────────────── 실패를 조용히 넘기지 않는 이유 ─────────────────────────
 *
 * 목적지가 실재하지 않으면 [AppException] 을 던진다. `next` 로 흘려보내면 **거절한
 * 사용자가 거절한 내용을 그대로 만난다** — 조용한 실패의 대가가 정확히 이 통제가 막으려던
 * 노출이다. 데이터 오류는 로드 시점의 `ScenarioYamlLoaderTest` 가 먼저 잡지만,
 * 런타임에서도 열어 두지 않는다.
 *
 * 목적지가 *선언되지 않은* 경우는 예외가 아니라 null 이다 — 그 Scene 에는 건너뛰기 문이
 * 없다는 뜻이고, 호출자는 평소대로 `Scene.next` 로 진행한다.
 *
 * 주의 — 동의 블록의 위치는 Scene 마다 다르다. 자기 카드를 띄우는 Scene 은
 * `trigger_warning`, 다른 Scene 의 카드에 덮이는 Scene 은 `consent_coverage` 다
 * (룻 Scene 2·5). 로더가 표준필드만 걷어내므로 yml 의 `extras:` 블록은 한 겹 더 들어간다.
 * [SceneConvergenceResolver.optionBlock] 과 같은 이유로 양쪽 다 본다.
 */
@Component
class SceneSkipResolver(
    private val keyExtractor: DecisionKeyExtractor,
) {

    /**
     * 이번 결정이 건너뛰기/거절이면 목적지를, 아니면 null.
     *
     * null 이면 호출자는 평소대로 `Scene.next` 로 진행한다.
     */
    fun resolve(scenario: Scenario, scene: Scenario.Scene, decision: Map<String, Any?>?): Skip? {
        val key = keyExtractor.extract(decision) ?: return null
        val consent = consentBlock(scene) ?: return null

        return when (key) {
            DECLINE -> declined(consent, scene)
            SKIP -> skipTarget(scenario, scene, consent)
            else -> null
        }
    }

    /**
     * **앞선 씬에서 고른 건너뛰기를 이 씬까지 들고 온다.**
     *
     * 동의 카드 하나가 여러 씬을 덮는다(`covers_scenes`). 룻의 중간 카드는 Scene 3 과 5 를
     * 함께 덮으므로, Scene 3 에서 건너뛰기를 고른 사용자는 **Scene 5 에 도착했을 때도**
     * 그 선택의 보호를 받아야 한다. 그러지 않으면 목적지(4)를 지나 `next` 를 따라 5로
     * 흘러들어가 카드가 덮기로 한 내용을 그대로 만난다 — 건너뛰기를 고른 채로.
     *
     * 덮인 씬은 `consent_coverage.covered_by_scene` 으로 자기를 덮은 씬을 지목한다.
     * 그 씬에 기록된 결정이 건너뛰기면 여기서 축약 경로를 돌려준다. 세션에 남은 결정이
     * 근거이므로 클라이언트가 다시 보내주지 않아도 된다.
     *
     * 마지막 씬의 축약 블록(문자열 목적지)에만 적용된다. 정수 목적지는 애초에 그 씬으로
     * 들어오지 않게 점프시키므로 들고 올 것이 없다.
     */
    fun carriedSkip(scene: Scenario.Scene, recordedDecisions: Map<String, Any?>): Skip.AltBlock? {
        val consent = consentBlock(scene) ?: return null
        val coveredBy = consent["covered_by_scene"] as? Int ?: return null
        val raw = consent[SKIP_KEY] ?: return null
        if (raw is Int) return null

        @Suppress("UNCHECKED_CAST")
        val decision = recordedDecisions["scene$coveredBy"] as? Map<String, Any?> ?: return null
        if (keyExtractor.extract(decision) != SKIP) return null

        val blockId = raw.toString()
        val block = conditionalBlock(scene, blockId)
            ?: throw AppException(
                ErrorCode.E_VALIDATION,
                "Scene ${scene.id}: 이어받은 skip 목적지 '$blockId' 에 해당하는 conditional_blocks 항목이 없다",
            )
        return Skip.AltBlock(blockId, overridesOf(block), rendersOf(block))
    }

    /** `declined_route` 집행. 현재 정의된 센티널은 `closing` 하나뿐이다. */
    private fun declined(consent: Map<*, *>, scene: Scenario.Scene): Skip? {
        val route = consent["declined_route"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        if (route != CLOSING) {
            throw AppException(
                ErrorCode.E_VALIDATION,
                "Scene ${scene.id}: 알 수 없는 declined_route '$route' — 정의된 값은 '$CLOSING' 뿐이다",
            )
        }
        return Skip.Closing
    }

    /** `skip_alternative_scene_id` 집행 — 정수면 Scene 점프, 문자열이면 같은 Scene 의 축약 블록. */
    private fun skipTarget(scenario: Scenario, scene: Scenario.Scene, consent: Map<*, *>): Skip? {
        val raw = consent[SKIP_KEY] ?: return null

        if (raw is Int) {
            if (scenario.scenes.none { it.id == raw }) {
                throw AppException(
                    ErrorCode.E_VALIDATION,
                    "Scene ${scene.id}: skip 목적지 Scene $raw 이 시나리오에 없다",
                )
            }
            return Skip.ToScene(raw)
        }

        val blockId = raw.toString().takeIf { it.isNotBlank() }
            ?: throw AppException(ErrorCode.E_VALIDATION, "Scene ${scene.id}: skip 목적지가 비어 있다")
        val block = conditionalBlock(scene, blockId)
            ?: throw AppException(
                ErrorCode.E_VALIDATION,
                "Scene ${scene.id}: skip 목적지 '$blockId' 에 해당하는 conditional_blocks 항목이 없다",
            )
        return Skip.AltBlock(blockId, overridesOf(block), rendersOf(block))
    }

    /** 축약 블록이 선언한 payload override — 메타 키(id·reached_by·renders·note)는 제외한다. */
    private fun overridesOf(block: Map<*, *>): Map<String, Any?> =
        block.entries
            .mapNotNull { (k, v) -> k?.toString()?.let { it to v } }
            .filterNot { (k, _) -> k in BLOCK_META_KEYS }
            .toMap()

    /** 블록이 "이건 남는다" 고 약속한 payload 키들. */
    private fun rendersOf(block: Map<*, *>): List<String> =
        (block["renders"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

    /** 이 Scene 의 동의 블록 — 자기 카드(`trigger_warning`) 우선, 없으면 덮인 표시(`consent_coverage`). */
    private fun consentBlock(scene: Scenario.Scene): Map<*, *>? {
        val roots = listOfNotNull(scene.extras, scene.extras?.get("extras") as? Map<*, *>)
        for (root in roots) {
            (root["trigger_warning"] as? Map<*, *>)?.let { return it }
            (root["consent_coverage"] as? Map<*, *>)?.let { return it }
        }
        return null
    }

    /** 같은 Scene 안의 `conditional_blocks` 에서 id 로 찾는다. */
    private fun conditionalBlock(scene: Scenario.Scene, blockId: String): Map<*, *>? {
        val roots = listOfNotNull(scene.extras, scene.extras?.get("extras") as? Map<*, *>)
        for (root in roots) {
            val blocks = root["conditional_blocks"] as? List<*> ?: continue
            blocks.filterIsInstance<Map<*, *>>()
                .firstOrNull { it["id"]?.toString() == blockId }
                ?.let { return it }
        }
        return null
    }

    /** 건너뛰기 목적지. */
    sealed interface Skip {
        /** 다른 Scene 으로 점프한다. */
        data class ToScene(val sceneId: Int) : Skip

        /**
         * 같은 Scene 에 머물되 축약 블록의 override 를 payload 에 덮는다.
         *
         * @param overrides 블록이 선언한 payload 치환 (룻 Scene 5: `captions` → 빈 목록)
         * @param renders 축약 경로에서도 남아야 한다고 블록이 약속한 payload 키들
         */
        data class AltBlock(
            val blockId: String,
            val overrides: Map<String, Any?>,
            val renders: List<String>,
        ) : Skip

        /** 카드를 거절했다 — 종결 화면으로 간다. */
        data object Closing : Skip
    }

    companion object {
        /** 클라이언트가 보내는 건너뛰기 결정값. 프론트 계약(`decision: { value: "skip" }`)과 같다. */
        const val SKIP = "skip"

        /** 카드 거절 결정값. */
        const val DECLINE = "decline"

        /** `declined_route` 의 유일한 센티널. */
        const val CLOSING = "closing"

        private const val SKIP_KEY = "skip_alternative_scene_id"

        private val BLOCK_META_KEYS = setOf("id", "reached_by", "renders", "note")
    }
}
