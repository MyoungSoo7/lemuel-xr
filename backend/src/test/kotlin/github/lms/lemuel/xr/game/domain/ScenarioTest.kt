package github.lms.lemuel.xr.game.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ScenarioTest {

    private fun scene(id: Int, next: Int?): Scenario.Scene =
        Scenario.Scene(
            id, "title$id", "interaction", "pick_one",
            60, "narr$id", "ref$id", false, next, mapOf("k" to "v"),
        )

    @Test
    fun `scene id로 조회`() {
        val s = Scenario(
            "joseph", "곡식 7년",
            listOf(scene(1, 2), scene(2, 3), scene(3, null)),
        )
        assertThat(s.scene(2).title).isEqualTo("title2")
        assertThat(s.scene(2).next).isEqualTo(3)
    }

    @Test
    fun `없는 scene id IllegalArgumentException`() {
        val s = Scenario("joseph", "곡식 7년", listOf(scene(1, 2)))
        assertThatThrownBy { s.scene(99) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Scene not found")
    }

    @Test
    fun `totalScenes 는 scene 수`() {
        val s = Scenario("moses", "광야", listOf(scene(1, 2), scene(2, null)))
        assertThat(s.totalScenes()).isEqualTo(2)
    }

    @Test
    fun `scene 레코드 접근자`() {
        val sc = scene(1, 2)
        assertThat(sc.id).isEqualTo(1)
        assertThat(sc.type).isEqualTo("interaction")
        assertThat(sc.interaction).isEqualTo("pick_one")
        assertThat(sc.durationSec).isEqualTo(60)
        assertThat(sc.narrationId).isEqualTo("narr1")
        assertThat(sc.scriptureRef).isEqualTo("ref1")
        assertThat(sc.realtimeLlm).isFalse()
        assertThat(sc.extras).containsEntry("k", "v")
    }
}
