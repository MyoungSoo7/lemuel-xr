package github.lms.lemuel.xr.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ScenarioYamlLoader 는 classpath 의 resources/scenarios/*.yml 을 읽으므로
 * Spring context 없이 @PostConstruct(loadAll) 를 직접 호출해 단위 검증한다.
 */
class ScenarioYamlLoaderTest {

    private static ScenarioYamlLoader loader;

    @BeforeAll
    static void loadAll() throws Exception {
        loader = new ScenarioYamlLoader();
        // package-private @PostConstruct loadAll() 을 리플렉션으로 호출.
        Method m = ScenarioYamlLoader.class.getDeclaredMethod("loadAll");
        m.setAccessible(true);
        m.invoke(loader);
    }

    @Test
    void 다섯인물이상_시나리오_로드됨() {
        // joseph/moses/david/jesus/job (+ elijah) — 최소 5개 로드.
        int loaded = 0;
        for (Character c : Character.values()) {
            try {
                loader.forCharacter(c);
                loaded++;
            } catch (AppException ignore) {
                // 미로드 인물
            }
        }
        assertThat(loaded).isGreaterThanOrEqualTo(5);
    }

    @Test
    void joseph_시나리오_구조() {
        Scenario s = loader.forCharacter(Character.JOSEPH);
        assertThat(s.character()).isEqualTo("joseph");
        assertThat(s.title()).isEqualTo("곡식 7년");
        assertThat(s.scenes()).isNotEmpty();
        Scenario.Scene s1 = s.scene(1);
        assertThat(s1.type()).isEqualTo("cinematic");
        assertThat(s1.title()).contains("파라오");
        // scene 2 monologues 는 표준필드가 아니므로 extras 로 보존
        assertThat(s.scene(2).extras()).containsKey("monologues");
    }

    @Test
    void jesus_시나리오_로드_outro_linked_values() {
        Scenario s = loader.forCharacter(Character.JESUS);
        assertThat(s.character()).isEqualTo("jesus");
        // outro (next==null) scene 에 linked_values 존재
        Scenario.Scene outro = s.scenes().stream()
                .filter(sc -> sc.next() == null).findFirst().orElseThrow();
        assertThat(outro.extras()).containsKey("linked_values");
        assertThat(outro.extras()).containsKey("value_prompt");
    }

    @Test
    void 표준필드는_extras에서_제거됨() {
        Scenario s = loader.forCharacter(Character.JOSEPH);
        Scenario.Scene s1 = s.scene(1);
        assertThat(s1.extras()).doesNotContainKeys(
                "id", "title", "type", "interaction", "duration_sec",
                "narration_id", "scripture_ref", "realtime_llm", "next");
    }

    @Test
    void 미로드_character_forCharacter_E_CHARACTER_UNKNOWN() {
        // 별도 loader 인스턴스 — loadAll 미호출 상태에서 cache miss.
        ScenarioYamlLoader empty = new ScenarioYamlLoader();
        assertThatThrownBy(() -> empty.forCharacter(Character.JOSEPH))
                .isInstanceOf(AppException.class);
    }
}
