package github.lms.lemuel.xr.safety

import github.lms.lemuel.xr.IntegrationTestBase
import github.lms.lemuel.xr.game.application.CrisisTokenResolver
import github.lms.lemuel.xr.game.application.ScenePayloadAssembler
import github.lms.lemuel.xr.game.application.ScenarioYamlLoader
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.safety.application.GetCrisisResourcesUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 위기 연락처 정본(109)이 시드 → 포트 → 시나리오 payload 까지 끊기지 않고 흐르는지 확인한다.
 *
 * 2024-01-01 부로 자살예방 상담은 109 로 통합됐다(보건복지부). 앱은 오랫동안 구 번호 1393 을
 * 위기 카피에 박아 두고 있었고, 그 문구는 위기 상태의 사용자가 직접 읽는 문장이었다.
 * 이제 시나리오는 `{{crisis_resources.default}}` 토큰만 갖고 실제 번호는 DB 정본에서 온다 —
 * 그 연결이 끊기면 사용자에게 *토큰 원문* 이 보인다. 그 최악을 여기서 막는다.
 */
class CrisisContactCanonicalIT : IntegrationTestBase() {

    @Autowired
    private lateinit var loader: ScenarioYamlLoader

    @Autowired
    private lateinit var payloads: ScenePayloadAssembler

    @Autowired
    private lateinit var crisisResources: GetCrisisResourcesUseCase

    // ───────────────────────── 시드 (V20260802031449) ─────────────────────────

    @Test
    fun `KR ko-KR 의 우선순위 1위 전화 자원은 109 다`() {
        val all = crisisResources.execute("KR", "ko-KR")
        val firstPhone = all.first { it.contactType == "phone" }

        assertThat(firstPhone.contactValue).isEqualTo("109")
        assertThat(firstPhone.name).contains("자살예방")
        assertThat(firstPhone.priority?.toInt()).isEqualTo(1)
    }

    @Test
    fun `1393 은 삭제되지 않고 구 번호로 강등돼 있다`() {
        // 출처 어디에도 1393 이 *불통* 이라는 근거는 없다 — 정번호 지위만 잃었다.
        val row = crisisResources.execute("KR", "ko-KR").first { it.contactValue == "1393" }

        assertThat(row.name).contains("구 번호")
        assertThat(row.priority?.toInt()).isGreaterThan(1)
    }

    @Test
    fun `1577-0199 · 1388 · 1366 · 1588-9191 은 폐지되지 않았다`() {
        // 자살예방 상담 기능만 109 로 통합됐을 뿐, 이 번호들은 담당 분야 상담을 계속한다.
        val values = crisisResources.execute("KR", "ko-KR").map { it.contactValue }

        assertThat(values).contains("1577-0199", "1388", "1366", "1588-9191")
    }

    // ───────────────────────── 시나리오 payload 치환 ─────────────────────────

    @Test
    fun `모든 인물 모든 씬 payload 에 원시 토큰이 남지 않는다`() {
        for (c in Character.entries) {
            val scenario = loader.forCharacter(c)
            for (scene in scenario.scenes) {
                val rendered = payloads.build(scenario, scene.id).toString()
                assertThat(rendered)
                    .`as`("%s scene %s", c.dbValue, scene.id)
                    .doesNotContain(CrisisTokenResolver.TOKEN)
                    .doesNotContain("{{")
            }
        }
    }

    @Test
    fun `위기 문구는 DB 정본 109 로 치환된다`() {
        val resolved = Character.entries.map { c ->
            val scenario = loader.forCharacter(c)
            c.dbValue to scenario.scenes.map { payloads.build(scenario, it.id).toString() }
        }

        for ((character, rendered) in resolved) {
            assertThat(rendered.any { it.contains("109") })
                .`as`("%s 의 어느 씬에도 위기 연락처가 없다 — R1 안전선 소실", character)
                .isTrue()
        }
    }
}
