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

    /**
     * 회귀 방지 — Character enum 에 값을 추가하고 yml 을 빠뜨리면 런타임에 조용히 사라진다.
     * ScenarioYamlLoader.loadAll() 은 리소스가 없으면 warn 로그만 남기고 넘어가기 때문에
     * (솔로몬이 설계 완료 후에도 배선되지 않았던 원인) 이 테스트가 유일한 게이트다.
     */
    @Test
    fun `모든 Character 에 시나리오 yml 이 존재하고 로드된다`() {
        val missing = Character.entries.filter {
            ScenarioYamlLoaderTest::class.java.classLoader
                .getResource("scenarios/${it.dbValue}.yml") == null
        }
        assertThat(missing)
            .describedAs("scenarios/{dbValue}.yml 누락 — enum 추가 시 yml 도 함께 추가해야 한다")
            .isEmpty()

        for (c in Character.entries) {
            val s = loader.forCharacter(c)   // 파싱 실패 시 캐시 미적재 → AppException
            assertThat(s.character)
                .describedAs("%s — yml 의 character 필드가 dbValue 와 일치해야 한다", c)
                .isEqualTo(c.dbValue)
            assertThat(s.title).describedAs("%s — title", c).isNotBlank()
        }
    }

    /**
     * next 체인 무결성 — 마지막 씬만 next: null, 나머지는 실재하는 scene id 를 가리킨다.
     * id 중복·자기참조·끊긴 링크를 전 인물에 대해 한 번에 잡는다.
     */
    @Test
    fun `모든 시나리오의 next 체인이 무결하다`() {
        for (c in Character.entries) {
            val s = loader.forCharacter(c)
            val ids = s.scenes.map { it.id }

            assertThat(s.scenes).describedAs("%s — scene 최소 1개", c).isNotEmpty()
            assertThat(ids).describedAs("%s — scene id 중복", c).doesNotHaveDuplicates()
            assertThat(s.totalScenes()).isEqualTo(ids.size)

            val terminals = s.scenes.filter { it.next == null }
            assertThat(terminals).describedAs("%s — next: null 인 종료 씬은 정확히 1개", c).hasSize(1)
            assertThat(terminals.single().id)
                .describedAs("%s — 종료 씬은 마지막 씬이어야 한다", c)
                .isEqualTo(s.scenes.last().id)
            assertThat(terminals.single().type).describedAs("%s — 종료 씬 type", c).isEqualTo("outro")

            for (scene in s.scenes) {
                val next = scene.next ?: continue
                assertThat(ids)
                    .describedAs("%s scene %d — next=%d 가 실재하지 않는 id", c, scene.id, next)
                    .contains(next)
                assertThat(next).describedAs("%s scene %d — 자기 참조", c, scene.id).isNotEqualTo(scene.id)
                // scene(next) 가 예외 없이 조회되는지까지 확인
                assertThat(s.scene(next).id).isEqualTo(next)
            }

            // trigger_warning 의 skip 경로도 실재하는 씬을 가리켜야 한다 (R4 우회 경로 무결성)
            for (scene in s.scenes) {
                @Suppress("UNCHECKED_CAST")
                val tw = scene.extras?.get("trigger_warning") as? Map<String, Any?> ?: continue
                val skipTo = tw["skip_alternative_scene_id"] as? Int ?: continue
                assertThat(ids)
                    .describedAs("%s scene %d — skip_alternative_scene_id=%d 미존재", c, scene.id, skipTo)
                    .contains(skipTo)
                assertThat(skipTo)
                    .describedAs("%s scene %d — skip 경로가 트리거 씬 자신 또는 그 이전을 가리킨다", c, scene.id)
                    .isGreaterThan(scene.id)

                /*
                  백엔드에는 "skip" 결정에 대한 별도 분기가 없다 — 결정을 기록하고 next 를 따라간다.
                  그래서 프론트의 건너뛰기는 *본문을 렌더하지 않고 그냥 진행* 하는 것으로 구현돼 있고,
                  두 값이 어긋나면 건너뛴 사람이 skip_alternative_scene_id 가 아니라 next 로 간다.
                  조용히 어긋난다 — 화면도 안 죽고 로그도 안 남는다. 그래서 여기서 못 박는다.

                  둘을 갈라야 할 진짜 이유가 생기면, 이 단언을 지우기 전에 백엔드에 skip 분기를 먼저 넣어라.
                */
                assertThat(skipTo)
                    .describedAs(
                        "%s scene %d — skip_alternative_scene_id(%d) 와 next(%s) 가 다르다. " +
                            "백엔드는 skip 을 따로 처리하지 않으므로 건너뛴 사용자는 next 로 간다",
                        c, scene.id, skipTo, scene.next,
                    )
                    .isEqualTo(scene.next)
            }
        }
    }

    /**
     * R4 게이트가 붙어 있어야 하는 씬 — yml 에서 `trigger_warning` 이 사라지면 여기서 빨개진다.
     *
     * 위의 무결성 루프는 *있는* 경고만 검사한다. 경고를 통째로 지우면 검사할 게 없어져서
     * 전부 초록으로 통과한다 — 즉 안전 통제를 제거하는 편집이 가장 조용하다. 그 구멍을 막는다.
     *
     * 대상 3씬은 2026-08-12 프론트 전수 조사에서 배선이 깨져 있던 곳이다
     * (jesus 는 payload 미독취 · david 는 레거시 boolean · joseph 은 카드 자체 부재).
     * yml 쪽 선언이 이 테스트로 고정되고, 화면이 그 선언을 읽는지는
     * `scripts/check_frontend_trigger_warning.py` 가 CI 에서 따로 본다. 둘 다 있어야 배선이 산다.
     *
     * 여기서 문구의 적절성은 재지 않는다 — 그건 사람(정신건강 검토)의 몫이다.
     */
    @Test
    fun `수난·골리앗·재회 씬에 R4 트리거 경고가 선언돼 있다`() {
        data class Expected(
            val character: Character,
            val sceneId: Int,
            val content: List<String>,
            val what: String,
        )

        val cases = listOf(
            Expected(Character.JESUS, 5, listOf("suffering", "death"), "겟세마네·십자가"),
            Expected(Character.DAVID, 5, listOf("violence"), "골리앗 전투"),
            Expected(Character.JOSEPH, 4, listOf("betrayal", "family_trauma"), "형제 재회"),
        )

        for (e in cases) {
            val scene = loader.forCharacter(e.character).scene(e.sceneId)

            @Suppress("UNCHECKED_CAST")
            val tw = scene.extras?.get("trigger_warning") as? Map<String, Any?>
            assertThat(tw)
                .describedAs(
                    "%s scene %d(%s) — trigger_warning 이 없다. 동의 게이트가 사라진다",
                    e.character, e.sceneId, e.what,
                )
                .isNotNull()
            requireNotNull(tw)

            assertThat(tw["level"] as? String)
                .describedAs("%s scene %d — level 이 있어야 화면이 강도를 표시한다", e.character, e.sceneId)
                .isNotBlank()

            @Suppress("UNCHECKED_CAST")
            val content = tw["content"] as? List<String>
            assertThat(content)
                .describedAs("%s scene %d — content 태그", e.character, e.sceneId)
                .containsAll(e.content)

            // 건너뛸 길이 없으면 그건 경고가 아니라 통보다. 목적지 무결성은 위 루프가 이미 본다.
            assertThat(tw["skip_alternative_scene_id"] as? Int)
                .describedAs("%s scene %d — skip_alternative_scene_id", e.character, e.sceneId)
                .isNotNull()

            assertThat(tw["consent_card_id"] as? String)
                .describedAs("%s scene %d — consent_card_id", e.character, e.sceneId)
                .isNotBlank()
        }
    }

    @Test
    fun `solomon 시나리오 구조 — 5 scene · R4 게이트 2곳 · 9조합 재정향`() {
        val s = loader.forCharacter(Character.SOLOMON)
        assertThat(s.character).isEqualTo("solomon")
        assertThat(s.title).isEqualTo("해 아래, 빈 손")
        assertThat(s.totalScenes()).isEqualTo(5)

        // R4 동의 게이트 — Scene 3(mid) · Scene 4(low~mid) 두 곳. skip 경로가 트리거를 실제로 회피한다.
        val gated = s.scenes.filter { it.extras?.containsKey("trigger_warning") == true }.map { it.id }
        assertThat(gated).containsExactly(3, 4)

        @Suppress("UNCHECKED_CAST")
        val tw3 = s.scene(3).extras!!["trigger_warning"] as Map<String, Any?>
        assertThat(tw3["level"]).isEqualTo("medium")
        assertThat(tw3["content"] as List<*>).contains("infant_loss")
        assertThat(tw3["skip_alternative_scene_id"]).isEqualTo(4)   // 재판 요약 자막 후 Scene 4
        // skip 경로가 트리거(칼·영아 상실)를 실제로 회피하는지 — 대체 자막이 씬 안에 있어야 한다.
        assertThat(inner(s.scene(3))).containsKey("skip_summary_caption")

        @Suppress("UNCHECKED_CAST")
        val tw4 = s.scene(4).extras!!["trigger_warning"] as Map<String, Any?>
        assertThat(tw4["skip_alternative_scene_id"]).isEqualTo(5)   // Scene 5 직행 (마지막 씬)
        assertThat(s.scenes.last().id).isEqualTo(5)

        // Scene 5 — hevel_label 3 × faith_tone 3 = 9 조합 + 라벨 없음 default.
        @Suppress("UNCHECKED_CAST")
        val matrix = inner(s.scene(5))["reorientation_texts"] as Map<String, Any?>
        assertThat(matrix).containsOnlyKeys("emptiness", "restlessness", "loss_of_meaning", "default")
        var combos = 0
        for (label in listOf("emptiness", "restlessness", "loss_of_meaning")) {
            @Suppress("UNCHECKED_CAST")
            val byTone = matrix[label] as Map<String, Any?>
            assertThat(byTone).containsOnlyKeys("strong", "balanced", "soft")
            byTone.values.forEach { assertThat(it as String).isNotBlank() }
            combos += byTone.size
        }
        assertThat(combos).isEqualTo(9)
        assertThat(matrix["default"] as String).isNotBlank()

        // Scene 4 의 허무 라벨 3종이 Scene 5 매트릭스 키와 정확히 일치해야 한다 (배선 무결성).
        @Suppress("UNCHECKED_CAST")
        val labels = (inner(s.scene(4))["options"] as List<Map<String, Any?>>).map { it["id"] }
        assertThat(labels).containsExactlyInAnyOrder("emptiness", "restlessness", "loss_of_meaning")
    }

    /**
     * R1 — 솔로몬 본문에는 자살사고가 없다. 전도서 4장 초반 죽음선호 구절의 인용·각색·암시 전면 금지.
     * 위기 전화번호 하드코딩도 금지 — 번호가 아니라 [CrisisTokenResolver.TOKEN] 만 둔다.
     *
     * `crisis_reminder` 는 *있어야* 한다. 이 PR 최초 작성 시점에는 토큰 리졸버가 없어 필드를
     * 비워 뒀고 이 테스트도 부재를 단언했지만, #18 이 리졸버와 정본 카탈로그를 들여왔다.
     * Scene 4(허무)는 이 미션에서 R1 감도가 가장 높은 씬이라 outro 와 함께 위기 자원을 노출한다 —
     * 씬 자체가 위기 문구 없이 배포되는 것이 원래 이 필드를 비워 둔 것보다 큰 구멍이다.
     */
    @Test
    fun `solomon yml 은 죽음선호 구절과 하드코딩 위기번호를 포함하지 않는다`() {
        val raw = ScenarioYamlLoaderTest::class.java.classLoader
            .getResourceAsStream("scenarios/solomon.yml")!!
            .readBytes().toString(Charsets.UTF_8)

        val forbidden = listOf(
            "1393", "109", "1577-0199",                        // 위기 전화번호 하드코딩
            "죽는 날", "죽은 자", "출생하지", "태어나지 아니",      // 전 4:2~3 죽음선호 어구
            "죽고 싶", "사라지고 싶", "생명을 거두",
        )
        for (token in forbidden) {
            assertThat(raw).describedAs("solomon.yml 금칙 문자열: %s", token).doesNotContain(token)
        }
    }

    /** R1 — 위기 자원은 outro 와 Scene 4(허무)에 토큰으로 존재한다. 번호는 런타임에 주입된다. */
    @Test
    fun `solomon 의 위기 문구는 토큰으로만 존재한다`() {
        val s = loader.forCharacter(Character.SOLOMON)

        for (sceneId in listOf(4, 5)) {
            val reminder = inner(s.scene(sceneId))["crisis_reminder"] as? String
            assertThat(reminder)
                .describedAs("Scene %d 에 crisis_reminder 가 있어야 한다 (R1)", sceneId)
                .isNotNull()
            assertThat(reminder!!).contains(CrisisTokenResolver.TOKEN)
            // 토큰을 걷어낸 나머지에 숫자가 남아 있으면 번호 하드코딩이다 (ScenarioHotlineRatchetTest 와 동일 규칙).
            assertThat(reminder.replace(CrisisTokenResolver.TOKEN, "").filter { it.isDigit() })
                .describedAs("위기 문구에는 토큰 외 숫자가 없어야 한다 — Scene %d", sceneId)
                .isEmpty()
        }
    }

    /**
     * `converges_to` 무결성 — 전 인물. 수렴 대상은 **같은 Scene 의 형제 선택지 id** 여야 한다.
     *
     * 엔진이 이제 이 값을 집행하기 때문에(SceneConvergenceResolver) 오타 하나가 곧
     * "그 선택지를 고르면 아무 일도 안 일어난다" 가 된다. 자기 자신을 가리키면 사용자가
     * 같은 씬에 갇히므로 그것도 막는다. 재고 텍스트는 있으면 비어 있지 않아야 한다.
     */
    @Test
    fun `모든 시나리오의 converges_to 가 형제 선택지를 가리킨다`() {
        var checked = 0
        for (c in Character.entries) {
            for (scene in loader.forCharacter(c).scenes) {
                for (block in listOf(scene.extras, inner(scene))) {
                    val options = block?.get("options") as? List<*> ?: continue
                    val maps = options.filterIsInstance<Map<*, *>>()
                    val ids = maps.mapNotNull { it["id"]?.toString() }
                    val reconsider = block["reconsider_texts"] as? Map<*, *>

                    for (o in maps) {
                        val target = o["converges_to"]?.toString() ?: continue
                        val self = o["id"]?.toString()
                        checked++

                        assertThat(ids)
                            .describedAs("%s scene %d — converges_to '%s' 가 형제 선택지에 없다", c, scene.id, target)
                            .contains(target)
                        assertThat(target)
                            .describedAs("%s scene %d — 선택지 '%s' 가 자기 자신으로 수렴한다", c, scene.id, self)
                            .isNotEqualTo(self)

                        val text = reconsider?.get(self)
                        if (text != null) {
                            assertThat(text.toString().isNotBlank())
                                .describedAs("%s scene %d — '%s' 의 재고 텍스트가 비었다", c, scene.id, self)
                                .isTrue()
                        }
                    }
                }
            }
        }
        // 0건이면 이 테스트는 아무것도 증명하지 못한다 — 빈 순회를 통과로 읽지 않는다.
        assertThat(checked).describedAs("converges_to 선언이 하나도 없다 — 순회 0회는 통과가 아니다").isPositive()
    }

    /**
     * yml 의 `extras:` 블록은 로더가 표준필드를 걷어낸 뒤 `extras["extras"]` 로 한 겹 더 들어간다.
     * (표준필드 목록에 "extras" 가 없기 때문 — 기존 로더 동작 그대로.)
     */
    private fun inner(scene: github.lms.lemuel.xr.game.domain.Scenario.Scene): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return scene.extras?.get("extras") as? Map<String, Any?> ?: emptyMap()
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
