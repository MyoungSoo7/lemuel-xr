package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.game.domain.Character
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * ScenarioYamlLoader 는 classpath 의 resources/scenarios yml 을 읽으므로
 * Spring context 없이 @PostConstruct(loadAll) 를 직접 호출해 단위 검증한다.
 */
class ScenarioYamlLoaderTest {

    companion object {
        private lateinit var loader: ScenarioYamlLoader

        @JvmStatic
        @BeforeAll
        fun loadAll() {
            loader = ScenarioYamlLoader()
            // @PostConstruct loadAll() 을 직접 호출.
            loader.loadAll()
        }
    }

    @Test
    fun `다섯인물이상 시나리오 로드됨`() {
        // joseph/moses/david/jesus/job (+ elijah) — 최소 5개 로드.
        var loaded = 0
        for (c in Character.entries) {
            try {
                loader.forCharacter(c)
                loaded++
            } catch (ignore: AppException) {
                // 미로드 인물
            }
        }
        assertThat(loaded).isGreaterThanOrEqualTo(5)
    }

    @Test
    fun `joseph 시나리오 구조`() {
        val s = loader.forCharacter(Character.JOSEPH)
        assertThat(s.character).isEqualTo("joseph")
        assertThat(s.title).isEqualTo("곡식 7년")
        assertThat(s.scenes).isNotEmpty()
        val s1 = s.scene(1)
        assertThat(s1.type).isEqualTo("cinematic")
        assertThat(s1.title).contains("파라오")
        // scene 2 monologues 는 표준필드가 아니므로 extras 로 보존
        assertThat(s.scene(2).extras).containsKey("monologues")
    }

    @Test
    fun `jesus 시나리오 로드 outro linked_values`() {
        val s = loader.forCharacter(Character.JESUS)
        assertThat(s.character).isEqualTo("jesus")
        // outro (next==null) scene 에 linked_values 존재
        val outro = s.scenes.first { it.next == null }
        assertThat(outro.extras).containsKey("linked_values")
        assertThat(outro.extras).containsKey("value_prompt")
    }

    @Test
    fun `표준필드는 extras에서 제거됨`() {
        val s = loader.forCharacter(Character.JOSEPH)
        val s1 = s.scene(1)
        assertThat(s1.extras).doesNotContainKeys(
            "id", "title", "type", "interaction", "duration_sec",
            "narration_id", "scripture_ref", "realtime_llm", "next",
        )
    }

    @Test
    fun `미로드 character forCharacter E_CHARACTER_UNKNOWN`() {
        // 별도 loader 인스턴스 — loadAll 미호출 상태에서 cache miss.
        val empty = ScenarioYamlLoader()
        assertThatThrownBy { empty.forCharacter(Character.JOSEPH) }
            .isInstanceOf(AppException::class.java)
    }
}
