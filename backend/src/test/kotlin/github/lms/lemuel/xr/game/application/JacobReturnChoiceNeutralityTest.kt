package github.lms.lemuel.xr.game.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * `scenarios/jacob.yml` 의 structural 게이트 두 개를 **기계로** 확인한다.
 * `R3_no_reconciliation_pressure` 와 `content_exclusion_jacob` 이 이 파일의 두 함수를
 * `verified_by` 로 지목한다 — 없으면 그 게이트들은 산문 두 문단일 뿐이다
 * (ContentSafetyGateEnforcementTest 의 매달린 참조 차단).
 *
 * ── 왜 이 인물에 이 검사인가 ──
 * 이 미션의 축은 **화해는 요구되지 않는다** 이다. 사용자를 가해자 자리에 앉혀 놓고
 * 「그러니 돌아가라」로 끝나면 이 미션은 정확히 반대의 물건이 된다. 그 반전은 세 가지
 * 방식으로 조용히 일어난다:
 *
 *  1. **세 갈래에 등급이 생긴다.** 「가지 않고 머문다」에 점수·순위·정답 표시가 붙거나,
 *     그 쪽 마감만 짧고 미지근해지면 세 카드는 「돌아감을 향한 진행 막대」가 되고
 *     머무는 사람은 **아직 거기** 인 사람이 된다.
 *  2. **해제 문장이 사라진다.** 마감 열 문장은 전부 「이것은 야곱에게 있었던 일이며,
 *     당신의 실제 관계에 적용하지 않아도 된다.」로 끝난다. 길고, 없어도 문장이 읽히고,
 *     그래서 다듬는 편집에 가장 먼저 잘려 나간다. 잘리는 순간 마감은 오늘 여기 앉은
 *     사람에게 내려지는 지시가 된다.
 *  3. **건너뛴 사람의 결말이 깎인다.** Scene 5 의 동의 카드를 건너뛴 사람이 마감 문구를
 *     못 받으면, 대면을 본 사람만 끝을 받는다. 그것이 곧 화해 압박이다.
 *
 * 셋 다 「나빠 보이는 편집」이 아니라 **다듬는 편집으로 일어난다.** 그래서 검사로 박아 둔다.
 *
 * ── 배제는 왜 「자구 목록」으로 재지 않는가 ──
 * 다니엘·에스더와 같은 이유다. 배제된 문장을 기대값으로 적으면 그 문장이 리포로 돌아오고,
 * `extras` 는 payload 로 통째 복사되므로 화면으로 나갈 길이 생긴다. 그래서 **허용 목록** 으로
 * 뒤집어 잰다 — 성구 시드에 등재된 스물두 참조 밖으로 나가면 반려다.
 *
 * 다만 이 인물에는 자구로 재는 것이 둘 있다. **씨름 상대의 정체** 와 **개역한글 표기** 다.
 * 둘 다 목록이 짧고 오염이 한 낱말로 일어난다 — 「천사」한 번, 「환도뼈」한 번이면 그걸로 끝이다.
 * 다만 상대를 지칭하는 낱말을 통째로 금지할 수는 없다. 창 32:28 자막 자체가 그 낱말을
 * 담고 있기 때문이다(「하나님과 및 사람들과 겨루어」). 본문이 쓴 것을 지울 수는 없다.
 * 그래서 **화자 라벨** 로 잰다 — 씨름하는 쪽의 입에 붙는 이름이 무엇이냐가 실제 지칭이고,
 * 본문 안의 낱말은 지칭이 아니다.
 */
class JacobReturnChoiceNeutralityTest {

    @Test
    fun `돌아가는 세 갈래에 등급이 없고 마감 열 문장이 같은 해제 문장으로 끝난다`() {
        val scenes = scenes()
        val scene3 = scenes.first { it["id"] == 3 }
        val scene5 = scenes.first { it["id"] == 5 }
        val extras3 = scene3["extras"].asMap()
        val extras5 = scene5["extras"].asMap()

        // (a) 갈래 셋 — 저작 정본의 구성 그대로다.
        @Suppress("UNCHECKED_CAST")
        val options = extras3["options"] as? List<Map<String, Any?>>
        assertThat(options)
            .describedAs("Scene 3 에 갈래가 없다 — 이 미션의 유일한 사용자 선택이 사라졌다")
            .isNotNull
        assertThat(options!!.map { it["id"] })
            .describedAs("갈래 구성이 바뀌었다. 셋은 저작 정본(content/jacob/scene3.yml)에서 왔다.")
            .containsExactlyInAnyOrder("send_ahead", "go_afraid", "stay_and_pray")

        // (b) 순서를 고정하지 않는다. 고정하면 세 장이 「돌아감을 향한 진행 막대」로 읽히고,
        //     그 순간 머무는 카드는 「아직 거기」가 된다.
        assertThat(extras3["card_order_policy"])
            .describedAs("카드 순서를 고정했다 — 고정 순서 자체가 등급이다")
            .isEqualTo("shuffle_per_session")

        // (c) 등급이 없다 — 분기에 얽힌 서브트리 어디에도 점수·순위·정답 류 **키** 가 없다.
        //     값(산문)은 일부러 안 본다. 이 인물의 산문은 「점수·순위·정답을 두지 않는다」처럼
        //     **금지를 선언하는 자리** 라서, 값까지 훑으면 규약을 적은 문장마다 빨간불이 켜진다.
        val ranking = listOf(scene3, scene5)
            .flatMap { keysOf(it, "scene${it["id"]}") }
            .filter { (_, key) -> RANK_KEYS.any { key.lowercase().contains(it) } }
            .map { "${it.first} → ${it.second}" }
            .sorted()
        assertThat(ranking)
            .describedAs(
                "돌아가는 세 갈래에 등급이 생겼다. 점수·순위·정답·진행 막대는 이 미션에서 금지다 — " +
                    "셋 다 정당하고 결말 품질에 차등이 없다는 것이 R3 축이다.",
            )
            .isEmpty()

        // (d) 세 갈래가 갈라지지 않는다. 고른 값이 이야기의 진행을 바꾸면 그 자체가 보상이다.
        assertThat(options.flatMap { it.keys })
            .describedAs(
                "갈래에 라벨 말고 다른 것이 붙었다(next · 점수 · 후속 자막 등). " +
                    "이 값이 쓰이는 자리는 Scene 5 의 마감 문구 하나뿐이다.",
            )
            .containsOnly("id", "label_ko")
        assertThat(scene3["next"])
            .describedAs("Scene 3 이 갈래마다 다른 곳으로 가면 그게 등급이다 — 셋 다 Scene 4 로 간다")
            .isEqualTo(4)

        // (e) 마감은 세 라벨 × 세 톤 + 라벨 없음. 라벨 없음은 톤으로 쪼개지 않는다.
        val closing = extras5["closing_texts"].asMap()
        assertThat(closing.keys)
            .describedAs("마감 문구의 라벨 구성이 바뀌었다 — 고르지 않은 사람(default)도 반드시 있어야 한다")
            .containsExactlyInAnyOrder("send_ahead", "go_afraid", "stay_and_pray", "default")
        for (label in listOf("send_ahead", "go_afraid", "stay_and_pray")) {
            assertThat(closing[label].asMap().keys)
                .describedAs("%s 의 톤 구성이 세 벌이 아니다 — 한 라벨만 얇아지면 그게 등급이다", label)
                .containsExactlyInAnyOrder("strong", "balanced", "soft")
        }
        assertThat(closing["default"].asMap().keys)
            .describedAs(
                "라벨 없음(default)을 톤 셋으로 쪼갰다. 저작은 여기를 한 문안(`all`)으로 두기로 했다 — " +
                    "라벨이 없는 사람에게 임재 강도를 조절할 근거가 없다. 셋으로 늘리는 「개선」을 하지 말 것.",
            )
            .containsExactly("all")

        // (f) 열 문장 전부가 같은 해제 문장으로 끝난다. 이 미션의 R3 축 자체다.
        val sentences = closing.values
            .flatMap { it.asMap().entries }
            .map { it.key to it.value.toString() }
        assertThat(sentences)
            .describedAs("마감 문구가 열 문장이 아니다 — 라벨 3 × 톤 3 + 라벨 없음 1")
            .hasSize(10)
        val missing = sentences.filterNot { it.second.trim().endsWith(RELEASE_CLAUSE) }.map { it.first }
        assertThat(missing)
            .describedAs(
                "마감 문구에서 「%s」가 빠졌다. 이 문장이 없으면 마감은 오늘 여기 앉은 사람에게 " +
                    "내려지는 지시가 된다 — 열 문장 전부가 이 문장으로 끝나는 것이 저작 결정이다.",
                RELEASE_CLAUSE,
            )
            .isEmpty()

        // (g) 동의 카드를 건너뛴 사람도 같은 마감에 도달한다.
        //     건너뛴 사용자의 결말이 깎이면 그것이 곧 화해 압박이다.
        @Suppress("UNCHECKED_CAST")
        val blocks = extras5["conditional_blocks"] as? List<Map<String, Any?>>
        val alt = blocks?.firstOrNull { it["id"] == "5_alt" }
        assertThat(alt)
            .describedAs("Scene 5 의 축약 경로(5_alt)가 없다 — 동의 카드의 건너뛰기 목적지가 사라졌다")
            .isNotNull
        @Suppress("UNCHECKED_CAST")
        val renders = (alt!!["renders"] as? List<Any?>)?.map { it.toString() } ?: emptyList()
        assertThat(renders)
            .describedAs("건너뛴 사람이 마감 문구를 못 받는다 — 대면을 본 사람만 끝을 받으면 그게 압박이다")
            .contains("closing_texts")
        assertThat(alt.keys)
            .describedAs("축약 경로가 closing_texts 를 자기 것으로 덮어썼다 — 본 경로와 같은 마감이어야 한다")
            .doesNotContain("closing_texts")
    }

    @Test
    fun `씨름 상대를 지칭하지 않고 개역한글 표기를 쓰지 않는다`() {
        val texts = userFacingScalars()
        assertThat(texts)
            .describedAs("jacob.yml 에서 스칼라를 한 건도 못 읽었다 — 파일 탐색이나 파싱이 깨졌다")
            .isNotEmpty()

        // (a) 한 낱말로 일어나는 오염 둘. 목록이 짧아서 자구로 잴 수 있다.
        val banned = texts
            .flatMap { (path, text) -> BANNED_WORDS.filter { text.contains(it) }.map { "$path → $it" } }
            .distinct()
            .sorted()
        assertThat(banned)
            .describedAs(
                "금지된 낱말이 노출 텍스트에 들어왔다. 「천사」는 본문이 이름을 대지 않은 자리를 " +
                    "해설이 채운 것이고(창 32:24 는 「어떤 사람」이다), 「환도뼈」는 개역한글 표기다 — " +
                    "개역개정은 「허벅지 관절」(32:25) · 「허벅다리」(32:31) 다.",
            )
            .isEmpty()

        // (b) 씨름 상대의 지칭은 **화자 라벨** 로 잰다. 본문 안의 낱말은 지칭이 아니다 —
        //     창 32:28 자막이 그 낱말을 담고 있고, 본문이 쓴 것을 지울 수는 없다.
        val scene4 = scenes().first { it["id"] == 4 }
        val extras4 = scene4["extras"].asMap()

        @Suppress("UNCHECKED_CAST")
        val npcs = (extras4["npcs"] as? List<Map<String, Any?>>).orEmpty()
        assertThat(npcs.map { it["name_ko"] })
            .describedAs("얍복의 상대에게 이름이 붙었다 — 본문은 「어떤 사람」이라고만 한다(창 32:24)")
            .containsExactly("어떤 사람")

        @Suppress("UNCHECKED_CAST")
        val captions = (extras4["captions"] as? List<Map<String, Any?>>).orEmpty()
        assertThat(captions)
            .describedAs("Scene 4 에 자막이 없다 — 검사 대상이 비면 아래 판정은 아무것도 재지 않는다")
            .isNotEmpty()
        assertThat(captions.map { it["speaker_ko"].toString() }.distinct())
            .describedAs(
                "Scene 4 의 화자 라벨에 씨름 상대를 지칭하는 이름이 들어왔다. " +
                    "말하는 쪽의 입에 붙는 이름이 곧 지칭이다(저작 disputed #1, 신학검토자 승인 대기).",
            )
            .containsOnly("해설", "어떤 사람", "야곱의 속말")

        // (c) 배제 구간이 새지 않는다 — 허용 목록 밖의 창세기 참조는 반려다.
        val refs = texts.flatMap { (path, text) ->
            REF.findAll(text).map { path to "${it.groupValues[1]}:${it.groupValues[2]}" }
        }
        assertThat(refs)
            .describedAs("gen- 참조가 0건이다 — 검사 대상이 비면 아래 판정은 아무것도 재지 않는다")
            .isNotEmpty()

        val cited = texts.flatMap { (path, text) ->
            CITATION.findAll(text).flatMap { m ->
                val chapter = m.groupValues[1]
                val from = m.groupValues[2].toInt()
                val to = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: from
                (from..to).map { path to "$chapter:$it" }
            }
        }
        assertThat(cited)
            .describedAs("괄호 인용이 0건이다 — 자막 표기가 바뀌었으면 CITATION 을 같이 고칠 것")
            .isNotEmpty()

        val outside = (refs + cited).filter { it.second !in ALLOWED }
            .map { "${it.first} → 창 ${it.second}" }
            .distinct()
            .sorted()
        assertThat(outside)
            .describedAs(
                "허용 목록 밖의 창세기 참조가 노출 텍스트에 들어왔다. 허용 목록은 성구 시드에 " +
                    "등재된 스물두 참조다. 특히 라반 밑에서의 이십 년(창 29~31)은 미션 범위에서 " +
                    "배제됐다 — 서술하는 순간 「속인 사람이 속았다」는 인과응보 서사가 만들어지고, " +
                    "그건 이 미션이 세우지 않기로 한 구조다. " +
                    "여는 것이 옳다고 판단했다면 시드와 문서를 먼저 고칠 것.",
            )
            .isEmpty()

        // 시작점과 종료점이 실재한다 — 상한만 지키고 양 끝이 사라지면 빈 초록이 된다.
        assertThat(cited.map { it.second })
            .describedAs("창 27:19 인용이 없다 — 이 미션의 시작점이다")
            .contains("27:19")
        assertThat(cited.map { it.second })
            .describedAs("창 33:11 인용이 없다 — 이 미션의 종료점이다")
            .contains("33:11")
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun root(): Map<String, Any?> {
        val url = requireNotNull(javaClass.classLoader.getResource("scenarios/jacob.yml")) {
            "클래스패스에 scenarios/jacob.yml 이 없다"
        }
        return File(url.toURI()).reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun scenes(): List<Map<String, Any?>> = root()["scenes"] as List<Map<String, Any?>>

    /** jacob.yml 의 스칼라 (경로, 값). `safety_gates` 서브트리는 뺀다 — 배제 선언이 거기 있다. */
    private fun userFacingScalars(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for ((k, v) in root()) {
            if (k == "safety_gates") continue
            collect(v, k.toString(), out)
        }
        return out
    }

    /** 키 이름만 본다 — `score:` 처럼 값이 아니라 **키** 로 등급이 들어오는 쪽이 흔하다. */
    private fun keysOf(node: Any?, path: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun walk(n: Any?, p: String) {
            when (n) {
                is Map<*, *> -> n.forEach { (k, v) -> out += "$p.$k" to k.toString(); walk(v, "$p.$k") }
                is List<*> -> n.forEachIndexed { i, v -> walk(v, "$p[$i]") }
                else -> Unit
            }
        }
        walk(node, path)
        return out
    }

    private fun collect(node: Any?, path: String, out: MutableList<Pair<String, String>>) {
        when (node) {
            is String -> out += path to node
            is Map<*, *> -> node.forEach { (k, v) -> collect(v, "$path.$k", out) }
            is List<*> -> node.forEachIndexed { i, v -> collect(v, "$path[$i]", out) }
            else -> Unit
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()

    private companion object {
        /** 마감 열 문장이 전부 이 문장으로 끝난다. 자구를 바꾸지 않는다. */
        const val RELEASE_CLAUSE = "이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 된다."

        /** 기계가 읽는 참조 표기 — `scripture_ref` · `additional_refs`. */
        val REF = Regex("""\bgen-(\d+):(\d+)""")

        /** 사람이 읽는 인용 표기 — 자막 끝의 `(창 32:25)` · 범위꼴 `(창 32:9-10)`. */
        val CITATION = Regex("""\(창\s*(\d+):(\d+)(?:-(\d+))?\)""")

        /**
         * 허용된 참조 — `V20260822120000__seed_newchar_passages_gae.sql` 의 `gen-*` 스물두 건.
         * 시드에 없는 절을 자막에 쓰면 `scripts/scripture_ref_check.py` 도 잡지만, 그건 저작
         * 트리를 보고 이건 **실제로 로드되는 산출물** 을 본다.
         */
        val ALLOWED = setOf(
            "27:19", "27:34", "27:35", "27:36", "27:41",
            "32:9", "32:10", "32:11", "32:24", "32:25", "32:26", "32:27", "32:28", "32:30", "32:31",
            "33:1", "33:3", "33:4", "33:8", "33:9", "33:10", "33:11",
        )

        /** 등급을 들여오는 흔한 **키** 이름들. 값(산문)에는 적용하지 않는다 — 위 (c) 주석 참조. */
        val RANK_KEYS = listOf("score", "rank", "is_correct", "correct_answer", "progress_bar", "stepper")

        /** 한 낱말로 오염이 일어나는 자리. 짧아서 자구로 잰다. */
        val BANNED_WORDS = listOf("천사", "환도뼈")
    }
}
