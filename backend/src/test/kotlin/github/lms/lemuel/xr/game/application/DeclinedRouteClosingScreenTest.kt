package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * `declined_route: closing` 을 선언한 시나리오는 **종결 화면을 실제로 저작해 두었는가.**
 *
 * ─────────────────────────── 왜 정적으로도 재나 ───────────────────────────
 *
 * [DecideSceneUseCase] 는 종결 화면이 없으면 던진다. 그런데 그 던짐이 실행되는 순간은
 * **사용자가 카드를 거절한 바로 그때**다. 거절은 흔한 경로가 아니라서, 저작이 빠진 채로
 * 배포되면 한참 뒤에 실사용자 한 명에게서 처음 터진다. 그 사람은 이미 "이 내용을 보지
 * 않겠다" 고 고른 사람이다 — 가장 조심해야 할 사용자에게 500 을 주게 된다.
 *
 * 그래서 같은 불변식을 **로드되는 모든 시나리오에 대고 미리** 잰다. 런타임 던짐은 최후의
 * 방어이고, 이 테스트가 그 앞에 선다.
 *
 * ─────────────────────────── 무엇을 재지 않나 ───────────────────────────
 *
 * 종결 화면의 **내용**은 안 본다. 자막이 옳은지, `ui_overlays` 가 충분한지는 여기 밖이다.
 * 여기서 재는 것은 "거절한 사용자가 착지할 화면이 존재하는가" 하나다.
 */
class DeclinedRouteClosingScreenTest {

    companion object {
        private lateinit var loaded: Map<String, Scenario>

        @JvmStatic
        @BeforeAll
        fun loadAll() {
            val loader = ScenarioYamlLoader()
            loader.loadAll()
            loaded = Character.entries.mapNotNull { c ->
                try {
                    c.dbValue to loader.forCharacter(c)
                } catch (ignore: AppException) {
                    null // yml 이 아직 없는 인물 — 이 테스트의 대상이 아니다
                }
            }.toMap()
        }

        /**
         * yml Scene 이 자기 `extras:` 블록을 명시하면 그 안의 키는 `scene.extras["extras"]`
         * 로 한 겹 더 들어간다. 룻은 `trigger_warning` 을 최상위에, `closing_screen` 을
         * 중첩 블록에 두었다 — **한 파일 안에서 두 깊이가 섞인다.** 그래서 둘 다 본다.
         */
        private fun roots(scene: Scenario.Scene): List<Map<*, *>> =
            listOfNotNull(scene.extras, scene.extras?.get("extras") as? Map<*, *>)

        /** 저작이 거절 목적지를 선언한 자리 — `scenes[].…trigger_warning.declined_route`. */
        private fun declaresClosing(scenario: Scenario): Boolean = scenario.scenes.any { scene ->
            roots(scene).any { root ->
                val warning = root["trigger_warning"] as? Map<*, *>
                warning?.get("declined_route")?.toString() == "closing"
            }
        }
    }

    @Test
    fun `거절 경로를 선언한 시나리오는 종결 화면을 갖는다`() {
        val offenders = loaded.filterValues { declaresClosing(it) }
            .filterValues { scenario ->
                scenario.scenes.none { s -> roots(s).any { it["closing_screen"] is Map<*, *> } }
            }
            .keys.sorted()

        assertThat(offenders)
            .describedAs(
                "declined_route: closing 을 선언했는데 extras.closing_screen 이 없는 시나리오: %s. " +
                    "카드를 거절한 사용자가 착지할 화면이 저작되지 않았다 — 그 화면에 " +
                    "위기 안내와 나가기 버튼이 들어간다",
                offenders,
            )
            .isEmpty()
    }

    /**
     * 룻은 리포 전체에서 유일한 `declined_route` 보유자다. 그 사실이 조용히 바뀌면
     * 위 테스트가 **아무것도 재지 않는 상태**로 초록이 될 수 있다(공집합에 대한 전칭명제).
     * 그래서 대상이 실제로 존재하는지를 따로 못박는다.
     */
    @Test
    fun `거절 경로를 선언한 시나리오가 실제로 있다`() {
        val declaring = loaded.filterValues { declaresClosing(it) }.keys.sorted()

        assertThat(declaring)
            .describedAs("declined_route 보유 시나리오가 0건이면 위 검사는 빈 집합을 통과시킨다")
            .contains("ruth")
    }
}
