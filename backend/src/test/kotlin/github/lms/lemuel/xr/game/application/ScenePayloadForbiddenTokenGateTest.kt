package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.game.domain.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * 같은 yml 문장이 **두 출구** 로 나간다는 사실에 대한 회귀 가드.
 *
 * `monologues`/`outcomes`/`reactions` 는 Scene extras 의 키이고,
 * [ScenePayloadAssembler.build] 가 `sc.extras` 를 **통째로** payload 에 복사한다. 그래서
 * 같은 맵이 `/start`·`/decide` 의 `scenePayload` 로도 나간다 —
 * [ResponseResolver.matchResponseText] 를 거치지 않고.
 *
 * responseText 만 막고 끝내면 *같은 요청의* scenePayload 에 원문이 그대로 남는다.
 * 이 테스트는 두 경로가 **같은 대체 문구** 를 내는지를 못 박는다.
 *
 * 현재 시나리오 yml 에는 이 키들이 아직 없다 — 인물 디렉터리의 scene yml 을 `^monologues:` 로 훑으면 0건이다.
 * 즉 이건 앞을 보는 게이트다 — 신규 인물이 이 키를 채우는 순간 효력이 생긴다.
 */
class ScenePayloadForbiddenTokenGateTest {

    private val fallbackText = SafetyGateFixtures.FALLBACK_TEXT
    private val forbidden = "믿음이 부족해서 그런 일을 겪은 겁니다."

    // 두 출구가 *같은* 집행기를 공유하는지가 핵심이므로 인스턴스 하나를 양쪽에 넣는다.
    private val sanitizer = SafetyGateFixtures.sanitizer()

    private val assembler = ScenePayloadAssembler(CrisisTokenResolver { _, _ -> "109" }, sanitizer)
    private val resolver = ResponseResolver(
        mock<GenerateLlmResponseUseCase>(), DecisionKeyExtractor(), { true }, sanitizer,
    )

    private fun scene(extras: Map<String, Any?>?) = Scenario.Scene(
        4, "t", "interaction", null, null, null, null, null, 5, extras,
    )

    private fun build(extras: Map<String, Any?>?): Map<String, Any?> =
        assembler.build(Scenario("joseph", "t", listOf(scene(extras))), 4)

    @Test
    fun `scenePayload 로 새어나가는 monologues 원문도 대체된다`() {
        val payload = build(mapOf("monologues" to mapOf("give_up" to forbidden)))

        assertThat(payload.toString()).doesNotContain(forbidden)
        @Suppress("UNCHECKED_CAST")
        assertThat(payload["monologues"] as Map<String, Any?>)
            .containsEntry("give_up", fallbackText)
    }

    @Test
    fun `responseText 와 scenePayload 는 같은 대체 문구를 낸다`() {
        // 두 출구가 서로 다른 문구를 내면 사용자는 한 화면에서 다른 두 사과문을 읽는다.
        val extras = mapOf("monologues" to mapOf("give_up" to forbidden))

        val viaResponseText = resolver.matchResponseText(scene(extras), mapOf("value" to "give_up"))
        @Suppress("UNCHECKED_CAST")
        val viaPayload = (build(extras)["monologues"] as Map<String, Any?>)["give_up"]

        assertThat(viaResponseText).isEqualTo(fallbackText)
        assertThat(viaPayload).isEqualTo(viaResponseText)
    }

    @Test
    fun `outcomes 와 reactions 도 같은 게이트를 받는다`() {
        val payload = build(
            mapOf(
                "outcomes" to mapOf("a" to forbidden),
                "reactions" to mapOf("b" to "이제 그만하고  빨리   회복 하세요"),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        assertThat(payload["outcomes"] as Map<String, Any?>).containsEntry("a", fallbackText)
        // 공백 정규화도 payload 경로에 적용된다 — 저작 yml 은 줄바꿈이 섞이기 쉽다.
        @Suppress("UNCHECKED_CAST")
        assertThat(payload["reactions"] as Map<String, Any?>).containsEntry("b", fallbackText)
    }

    @Test
    fun `걸린 leaf 만 교체하고 형제 텍스트와 자료구조는 보존한다`() {
        // 맵째로 대체 문구로 갈아치우면 나머지 분기가 사라지고 클라이언트 자료구조가 무너진다.
        val payload = build(
            mapOf("monologues" to mapOf("bad" to forbidden, "good" to "요셉은 형들 앞에서 울었다")),
        )

        @Suppress("UNCHECKED_CAST")
        val monos = payload["monologues"] as Map<String, Any?>
        assertThat(monos).hasSize(2)
        assertThat(monos).containsEntry("bad", fallbackText)
        assertThat(monos).containsEntry("good", "요셉은 형들 앞에서 울었다")
    }

    @Test
    fun `중첩 map 과 list 안쪽 leaf 까지 훑는다`() {
        val payload = build(
            mapOf(
                "branches" to listOf(
                    mapOf("id" to "b1", "text" to forbidden),
                    mapOf("id" to "b2", "text" to "괜찮습니다"),
                ),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val branches = payload["branches"] as List<Map<String, Any?>>
        assertThat(branches[0]).containsEntry("text", fallbackText)
        // 구조 키(id)는 값도 키도 건드리지 않는다.
        assertThat(branches[0]).containsEntry("id", "b1")
        assertThat(branches[1]).containsEntry("text", "괜찮습니다")
    }

    @Test
    fun `구조 필드는 그대로 남는다`() {
        val payload = build(mapOf("monologues" to mapOf("give_up" to forbidden)))

        assertThat(payload).containsEntry("sceneId", 4)
        assertThat(payload).containsEntry("title", "t")
        assertThat(payload).containsEntry("type", "interaction")
        assertThat(payload).containsEntry("next", 5)
    }

    @Test
    fun `깨끗한 payload 는 한 글자도 바뀌지 않는다`() {
        val extras = mapOf("monologues" to mapOf("give_up" to "요셉은 형들 앞에서 울었다"))

        assertThat(build(extras)).isEqualTo(
            mapOf(
                "sceneId" to 4, "title" to "t", "type" to "interaction", "next" to 5,
                "monologues" to mapOf("give_up" to "요셉은 형들 앞에서 울었다"),
            ),
        )
    }

    @Test
    fun `위기 토큰 치환 뒤에 금지 토큰을 검사한다`() {
        // 순서가 뒤바뀌면 치환으로 만들어진 최종 문자열이 검사되지 않는다.
        // 사용자가 실제로 읽게 될 문자열이 검사 대상이어야 한다.
        val payload = build(
            mapOf("crisis_reminder" to "도움이 필요하면 ${CrisisTokenResolver.TOKEN} 로 연락하세요"),
        )

        assertThat(payload).containsEntry("crisis_reminder", "도움이 필요하면 109 로 연락하세요")
    }
}
