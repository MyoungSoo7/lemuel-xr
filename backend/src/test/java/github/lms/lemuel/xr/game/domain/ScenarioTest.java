package github.lms.lemuel.xr.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioTest {

    private Scenario.Scene scene(int id, Integer next) {
        return new Scenario.Scene(id, "title" + id, "interaction", "pick_one",
                60, "narr" + id, "ref" + id, false, next, Map.of("k", "v"));
    }

    @Test
    void scene_id로_조회() {
        Scenario s = new Scenario("joseph", "곡식 7년",
                List.of(scene(1, 2), scene(2, 3), scene(3, null)));
        assertThat(s.scene(2).title()).isEqualTo("title2");
        assertThat(s.scene(2).next()).isEqualTo(3);
    }

    @Test
    void 없는_scene_id_IllegalArgumentException() {
        Scenario s = new Scenario("joseph", "곡식 7년", List.of(scene(1, 2)));
        assertThatThrownBy(() -> s.scene(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scene not found");
    }

    @Test
    void totalScenes_는_scene_수() {
        Scenario s = new Scenario("moses", "광야", List.of(scene(1, 2), scene(2, null)));
        assertThat(s.totalScenes()).isEqualTo(2);
    }

    @Test
    void scene_레코드_접근자() {
        Scenario.Scene sc = scene(1, 2);
        assertThat(sc.id()).isEqualTo(1);
        assertThat(sc.type()).isEqualTo("interaction");
        assertThat(sc.interaction()).isEqualTo("pick_one");
        assertThat(sc.durationSec()).isEqualTo(60);
        assertThat(sc.narrationId()).isEqualTo("narr1");
        assertThat(sc.scriptureRef()).isEqualTo("ref1");
        assertThat(sc.realtimeLlm()).isFalse();
        assertThat(sc.extras()).containsEntry("k", "v");
    }
}
