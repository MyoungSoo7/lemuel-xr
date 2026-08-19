package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.domain.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * R4 동의 카드의 건너뛰기·거절 목적지 집행 — [SceneSkipResolver] 단위 테스트.
 *
 * 여기서 재는 것은 하나다: **저작된 목적지가 실제로 지켜지는가.** 엔진이 이걸 안 보면
 * 건너뛰기 버튼은 화면에만 있고, 고른 사용자는 `next` 로 — 즉 건너뛰려던 내용으로 — 간다.
 */
class SceneSkipResolverTest {

    private val resolver = SceneSkipResolver(DecisionKeyExtractor())

    // ── 목적지가 next 와 다른 경우 (룻 Scene 1 의 형태) ──

    @Test
    fun `skip 은 next 가 아니라 skip_alternative_scene_id 로 간다`() {
        // 진입 카드가 Scene 1·2 를 함께 덮는다 — 건너뛰면 3으로 가야 한다.
        val scenario = scenarioOf(
            scene(1, next = 2, extras = triggerWarning(covers = listOf(1, 2), skipTo = 3)),
            scene(2, next = 3),
            scene(3, next = null),
        )

        val skip = resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "skip"))

        assertThat(skip).isEqualTo(SceneSkipResolver.Skip.ToScene(3))
    }

    @Test
    fun `덮인 씬의 consent_coverage 에 적힌 목적지도 집행한다`() {
        // 룻 Scene 2 — 자기 카드는 없고 Scene 1 카드에 덮인다. 목적지는 상속돼 있다.
        val scenario = scenarioOf(
            scene(1, next = 2),
            scene(2, next = 3, extras = consentCoverage(skipTo = 3)),
            scene(3, next = null),
        )

        val skip = resolver.resolve(scenario, scenario.scene(2), mapOf("value" to "skip"))

        assertThat(skip).isEqualTo(SceneSkipResolver.Skip.ToScene(3))
    }

    @Test
    fun `skip 이 아닌 결정은 null — 평소대로 next 로 간다`() {
        val scenario = scenarioOf(
            scene(1, next = 2, extras = triggerWarning(covers = listOf(1, 2), skipTo = 3)),
            scene(2, next = null),
        )

        assertThat(resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "stay"))).isNull()
        assertThat(resolver.resolve(scenario, scenario.scene(1), null)).isNull()
    }

    @Test
    fun `동의 블록이 없는 씬은 skip 을 보내도 null`() {
        val scenario = scenarioOf(scene(1, next = 2), scene(2, next = null))

        assertThat(resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "skip"))).isNull()
    }

    // ── 거절(decline)은 건너뛰기와 다른 동작 ──

    @Test
    fun `decline 은 종결로 간다 — skip 목적지가 있어도 그쪽이 아니다`() {
        // 룻 Scene 3 의 형태 — 카드가 두 문을 준다. skip 은 4로, decline 은 종결로.
        val extras = mapOf<String, Any?>(
            "trigger_warning" to mapOf(
                "covers_scenes" to listOf(3, 5),
                "skip_alternative_scene_id" to 4,
                "declined_route" to "closing",
            ),
        )
        val scenario = scenarioOf(scene(3, next = 4, extras = extras), scene(4, next = null))

        val skip = resolver.resolve(scenario, scenario.scene(3), mapOf("value" to "decline"))

        assertThat(skip).isEqualTo(SceneSkipResolver.Skip.Closing)
    }

    @Test
    fun `declined_route 가 없으면 decline 은 null`() {
        val scenario = scenarioOf(
            scene(1, next = 2, extras = triggerWarning(covers = listOf(1), skipTo = 2)),
            scene(2, next = null),
        )

        assertThat(resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "decline"))).isNull()
    }

    // ── 마지막 씬의 축약 블록 (문자열 목적지) ──

    @Test
    fun `문자열 목적지는 같은 씬의 축약 블록을 지목한다`() {
        val scenario = scenarioOf(scene(1, next = null, extras = altBlockScene()))

        val skip = resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "skip"))

        assertThat(skip).isInstanceOf(SceneSkipResolver.Skip.AltBlock::class.java)
        val alt = skip as SceneSkipResolver.Skip.AltBlock
        assertThat(alt.blockId).isEqualTo("alt_short")
        // 블록이 선언한 override 만 넘어온다 — 메타 키(id·reached_by·renders·note)는 아니다.
        assertThat(alt.overrides).isEqualTo(mapOf<String, Any?>("captions" to emptyList<String>()))
        assertThat(alt.renders).containsExactly("closing_lines", "closing_screen")
    }

    // ── 앞 씬에서 고른 건너뛰기를 들고 온다 ──

    @Test
    fun `덮은 씬에서 건너뛰었으면 덮인 씬에서도 축약 경로다`() {
        val scene = scene(5, next = null, extras = coveredAltScene(coveredBy = 3))

        val carried = resolver.carriedSkip(scene, mapOf("scene3" to mapOf("value" to "skip")))

        assertThat(carried?.blockId).isEqualTo("alt_short")
    }

    @Test
    fun `덮은 씬에서 계속을 골랐으면 들고 올 것이 없다`() {
        val scene = scene(5, next = null, extras = coveredAltScene(coveredBy = 3))

        assertThat(resolver.carriedSkip(scene, mapOf("scene3" to mapOf("value" to "continue")))).isNull()
        assertThat(resolver.carriedSkip(scene, emptyMap())).isNull()
    }

    @Test
    fun `정수 목적지는 들고 오지 않는다 — 애초에 그 씬으로 들어오지 않는다`() {
        val scene = scene(
            2, next = 3,
            extras = mapOf(
                "consent_coverage" to mapOf("covered_by_scene" to 1, "skip_alternative_scene_id" to 3),
            ),
        )

        assertThat(resolver.carriedSkip(scene, mapOf("scene1" to mapOf("value" to "skip")))).isNull()
    }

    // ── 조용한 실패를 만들지 않는다 ──

    @Test
    fun `없는 씬을 가리키면 던진다 — next 로 흘려보내지 않는다`() {
        val scenario = scenarioOf(
            scene(1, next = 2, extras = triggerWarning(covers = listOf(1, 2), skipTo = 9)),
            scene(2, next = null),
        )

        assertThatThrownBy { resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "skip")) }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("Scene 9")
            .extracting { e -> (e as AppException).code }
            .isEqualTo(ErrorCode.E_VALIDATION)
    }

    @Test
    fun `짝 없는 축약 블록 id 는 던진다`() {
        val extras = mapOf(
            "consent_coverage" to mapOf("skip_alternative_scene_id" to "nope"),
            "conditional_blocks" to listOf(mapOf("id" to "alt_short")),
        )
        val scenario = scenarioOf(scene(1, next = null, extras = extras))

        assertThatThrownBy { resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "skip")) }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("nope")
    }

    @Test
    fun `모르는 declined_route 센티널은 던진다`() {
        val extras = mapOf(
            "trigger_warning" to mapOf("declined_route" to "somewhere_else"),
        )
        val scenario = scenarioOf(scene(1, next = 2, extras = extras), scene(2, next = null))

        assertThatThrownBy { resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "decline")) }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("somewhere_else")
    }

    // ── 중첩 extras 도 본다 (솔로몬처럼 yml 에 extras: 블록을 쓴 인물) ──

    @Test
    fun `중첩된 extras 블록 안의 동의 블록도 찾는다`() {
        val extras = mapOf(
            "extras" to mapOf(
                "trigger_warning" to mapOf(
                    "covers_scenes" to listOf(1),
                    "skip_alternative_scene_id" to 2,
                ),
            ),
        )
        val scenario = scenarioOf(scene(1, next = 3, extras = extras), scene(2, next = 3), scene(3, next = null))

        assertThat(resolver.resolve(scenario, scenario.scene(1), mapOf("value" to "skip")))
            .isEqualTo(SceneSkipResolver.Skip.ToScene(2))
    }

    // ── fixtures ──

    private fun scenarioOf(vararg scenes: Scenario.Scene) = Scenario("test", "테스트", scenes.toList())

    private fun scene(id: Int, next: Int?, extras: Map<String, Any?>? = null): Scenario.Scene =
        Scenario.Scene(id, "장면$id", "interaction", "pick_one", 60, null, null, null, next, extras)

    private fun triggerWarning(covers: List<Int>, skipTo: Int): Map<String, Any?> = mapOf(
        "trigger_warning" to mapOf(
            "covers_scenes" to covers,
            "skip_alternative_scene_id" to skipTo,
        ),
    )

    private fun consentCoverage(skipTo: Int): Map<String, Any?> = mapOf(
        "consent_coverage" to mapOf(
            "inherited" to true,
            "skip_alternative_scene_id" to skipTo,
        ),
    )

    /** 다른 씬의 카드에 덮인 마지막 씬 — 축약 블록을 이어받을 수 있는 형태. */
    private fun coveredAltScene(coveredBy: Int): Map<String, Any?> = mapOf(
        "consent_coverage" to mapOf(
            "inherited" to true,
            "covered_by_scene" to coveredBy,
            "skip_alternative_scene_id" to "alt_short",
        ),
        "captions" to listOf("자막 1", "자막 2"),
        "conditional_blocks" to listOf(
            mapOf("id" to "alt_short", "renders" to listOf("closing_lines"), "captions" to emptyList<String>()),
        ),
    )

    private fun altBlockScene(): Map<String, Any?> = mapOf(
        "consent_coverage" to mapOf(
            "inherited" to true,
            "skip_alternative_scene_id" to "alt_short",
        ),
        "captions" to listOf("성문 낭독 1", "성문 낭독 2"),
        "closing_lines" to listOf("마감 한 줄"),
        "closing_screen" to mapOf("kind" to "closing"),
        "conditional_blocks" to listOf(
            mapOf(
                "id" to "alt_short",
                "reached_by" to "skip_from_scene5",
                "renders" to listOf("closing_lines", "closing_screen"),
                "captions" to emptyList<String>(),
                "note" to "축약 경로도 마감에는 도달한다",
            ),
        ),
    )
}
