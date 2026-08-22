package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.LemuelXrApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * `scenarios/esther.yml` 의 structural 게이트 두 개를 **기계로** 확인한다.
 * `R3_silence_is_also_deliverance` 와 `content_exclusion_esther` 가 이 파일을 지목한다 —
 * 없으면 그 게이트들은 산문 두 문단일 뿐이다(ContentSafetyGateEnforcementTest 의 매달린 참조 차단).
 *
 * ── 왜 이 인물에 이 검사인가 ──
 * 이 미션의 축은 **개시(disclosure)의 자기 결정권**이다. 「드러내는 것이 정답인 묵상이 아니다」가
 * 설계 전체를 지탱하는데, 그 문장은 두 가지 방식으로 조용히 무너진다:
 *
 *  1. **세 카드에 등급이 생긴다.** 침묵을 맨 뒤로 옮기거나, 점수·순위·「정답」 표시를 붙이거나,
 *     결말 문구를 침묵 쪽만 짧고 미지근하게 쓰면 카드는 「개시를 향한 진행 막대」가 된다.
 *     그러면 침묵을 고른 사람은 **아직 거기** 인 사람이 된다.
 *  2. **에 4:14 의 절반이 사라진다.** 「다른 데로 말미암아 놓임과 구원을 얻으려니와」가
 *     빠지면 본문은 곧바로 「네가 말하지 않으면 아무도 못 산다」가 된다. 본문이 스스로를
 *     방어하는 자리이고, 잘라내기가 가장 쉬운 자리이기도 하다(길고, 없어도 문장이 읽힌다).
 *
 * 둘 다 「나빠 보이는 편집」이 아니라 **다듬는 편집으로 일어난다.** 그래서 검사로 박아 둔다.
 *
 * ── 배제는 왜 「자구 목록」으로 재지 않는가 ──
 * 다니엘과 같은 이유다. 배제된 문장을 기대값으로 적으면 그 문장이 리포로 돌아오고,
 * `extras` 는 payload 로 통째 복사되므로 화면으로 나갈 길이 생긴다. 다만 다니엘은 종료점이
 * 한 장(6장) 안에 있어 **절 번호 상한** 으로 쟀는데, 에스더는 배제 구간이 여러 장에 흩어져
 * 있다(1장 전체 · 2장 서사 · 5:9-14 · 6장 전체 · 7:7-10 · 8:7-17 · 9장). 상한 하나로는
 * 못 잰다. 그래서 **허용 목록** 으로 뒤집어 잰다 — 성구 시드에 등재된 스물두 참조 밖으로
 * 나가면 반려다. 배제 자구는 어디에도 적지 않는다.
 */
class EstherDisclosureNeutralityTest {

    @Test
    fun `침묵이 맨 앞이고 세 카드에 등급이 없다 — 4-14 의 방어 절반절도 그대로 남아 있다`() {
        val scenes = scenes()
        val scene3 = scenes.first { it["id"] == 3 }
        val extras3 = scene3["extras"].asMap()

        // (a) 카드 셋 — 정본 순서에서 침묵이 맨 앞이다.
        @Suppress("UNCHECKED_CAST")
        val options = extras3["options"] as? List<Map<String, Any?>>
        assertThat(options)
            .describedAs("Scene 3 에 카드가 없다 — 이 미션의 유일한 사용자 선택이 사라졌다")
            .isNotNull
        assertThat(options!!.map { it["id"] })
            .describedAs(
                "카드 순서·구성이 바뀌었다. 침묵(nondisclosure)이 맨 앞인 것은 저작 결정이다 — " +
                    "고정 순서로 세우면 세 장은 「개시를 향한 진행 막대」로 읽히고, " +
                    "그 순간 맨 앞 카드는 「아직 거기」가 된다.",
            )
            .containsExactly("nondisclosure", "gather_first", "speak_now")

        // (b) 등급이 없다 — 분기에 얽힌 서브트리 어디에도 점수·순위·정답 류 **키** 가 없다.
        //     값(산문)은 일부러 안 본다. 이 인물의 산문은 「점수·순위·정답을 두지 않는다」·
        //     「드러내는 것이 정답인 묵상이 아닙니다」처럼 **금지를 선언하는 자리** 라서,
        //     값까지 훑으면 규약을 적은 문장마다 빨간불이 켜진다(다니엘의 axis 함정과 같은 모양).
        //     등급은 실제로는 키로 들어온다 — `score:` · `rank:` · `is_correct:`.
        val ranking = listOf(scene3, scenes.first { it["id"] == 5 })
            .flatMap { keysOf(it, "scene${it["id"]}") }
            .filter { (_, key) -> RANK_KEYS.any { key.lowercase().contains(it) } }
            .map { "${it.first} → ${it.second}" }
            .sorted()
        assertThat(ranking)
            .describedAs(
                "세 카드에 등급이 생겼다. 점수·순위·정답·진행 막대는 이 미션에서 금지다 — " +
                    "셋 다 정당하고 결말 품질에 차등이 없다는 것이 R3 축이다.",
            )
            .isEmpty()

        // (c) 결말은 세 라벨 × 세 톤 + 라벨 없음. 라벨 없음은 톤으로 쪼개지 않는다.
        val closing = scenes.first { it["id"] == 5 }["extras"].asMap()["closing_texts"].asMap()
        assertThat(closing.keys)
            .describedAs("마감 문구의 라벨 구성이 바뀌었다 — 고르지 않은 사람(default)도 반드시 있어야 한다")
            .containsExactlyInAnyOrder("nondisclosure", "gather_first", "speak_now", "default")
        for (label in listOf("nondisclosure", "gather_first", "speak_now")) {
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

        // (d) 4:14 의 방어 절반절 — 선언과 본문 **양쪽** 에 있어야 한다.
        //     선언만 있고 자막에서 빠지는 것이 이 게이트가 잡으려는 실제 사고다.
        val declared = extras3["mandatory_clause"] as? String
        assertThat(declared)
            .describedAs("Scene 3 이 mandatory_clause 선언을 잃었다")
            .isEqualTo(DEFENSIVE_CLAUSE)

        @Suppress("UNCHECKED_CAST")
        val captions = (extras3["captions"] as List<Map<String, Any?>>).map { it["text_ko"].toString() }
        val verse414 = captions.filter { it.contains("(에 4:14)") }
        assertThat(verse414)
            .describedAs("Scene 3 에 에 4:14 자막이 없다 — 선언만 남고 본문이 사라지면 이 검사는 빈 초록이 된다")
            .isNotEmpty()
        assertThat(verse414.all { it.contains(DEFENSIVE_CLAUSE) })
            .describedAs(
                "에 4:14 자막에서 「%s」가 빠졌다. 이 절반절이 없으면 본문은 " +
                    "「네가 말하지 않으면 아무도 못 산다」가 된다 — 본문이 스스로를 방어하는 자리다.",
                DEFENSIVE_CLAUSE,
            )
            .isTrue()
    }

    @Test
    fun `에스더 미션은 시드에 등재된 스물두 참조 밖으로 나가지 않는다`() {
        val texts = userFacingScalars()

        assertThat(texts)
            .describedAs("esther.yml 에서 스칼라를 한 건도 못 읽었다 — 파일 탐색이나 파싱이 깨졌다")
            .isNotEmpty()

        // (a) 기계가 읽는 참조: `est-C:V`
        val refs = texts.flatMap { (path, text) ->
            REF.findAll(text).map { path to "${it.groupValues[1]}:${it.groupValues[2]}" }
        }
        assertThat(refs)
            .describedAs("est- 참조가 0건이다 — 검사 대상이 비면 아래 판정은 아무것도 재지 않는다")
            .isNotEmpty()

        // (b) 사람이 읽는 인용: `(에 C:V)` · 범위꼴 `(에 7:3-4)` 도 펼친다.
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
            .map { "${it.first} → 에 ${it.second}" }
            .distinct()
            .sorted()
        assertThat(outside)
            .describedAs(
                "허용 목록 밖의 에스더서 참조가 노출 텍스트에 들어왔다. 허용 목록은 성구 시드에 " +
                    "등재된 스물두 참조이고, 그 밖(1장 · 2장 서사 · 5:9 이후의 하만 서사 · 6장 · " +
                    "7:7 이후 · 8:7 이후 · 9장)은 미션 범위에서 배제됐다. " +
                    "여는 것이 옳다고 판단했다면 시드와 문서를 먼저 고칠 것.",
            )
            .isEmpty()

        // 종료점이 실재한다 — 상한만 지키고 끝이 사라지면 빈 초록이 된다.
        assertThat(cited.map { it.second })
            .describedAs("에 8:6 인용이 없다 — 이 미션의 종료점이다")
            .contains("8:6")
    }

    @Test
    fun `배제 사유는 문서에 적혀 있고 시나리오 값에는 적혀 있지 않다`() {
        val doc = repoRoot().resolve("docs/MVP-ESTHER-CONTENT.md").toFile()
        assertThat(doc)
            .describedAs("배제 사유를 담은 문서가 없다 — 게이트의 structural_check 가 가리키는 자리다")
            .exists()
        val body = doc.readText(StandardCharsets.UTF_8)
        for (marker in EXCLUSION_MARKERS) {
            assertThat(body).describedAs("문서에 「%s」 배제 항목이 없다", marker).contains(marker)
        }

        val leaked = userFacingScalars()
            .filter { (_, text) -> EXCLUSION_MARKERS.any { text.contains(it) } }
            .map { it.first }
        assertThat(leaked)
            .describedAs(
                "배제 사유가 시나리오 *값* 에 들어왔다. extras 는 payload 로 통째 복사된다 — " +
                    "무엇을 왜 뺐는지는 문서와 주석에 두고 값에는 두지 않는다.",
            )
            .isEmpty()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun root(): Map<String, Any?> {
        val url = requireNotNull(javaClass.classLoader.getResource("scenarios/esther.yml")) {
            "클래스패스에 scenarios/esther.yml 이 없다"
        }
        return File(url.toURI()).reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun scenes(): List<Map<String, Any?>> = root()["scenes"] as List<Map<String, Any?>>

    /** esther.yml 의 스칼라 (경로, 값). `safety_gates` 서브트리는 뺀다 — 배제 선언이 거기 있다. */
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

    /** `DanielViolenceExclusionTest.repoRoot()` 와 같은 방식 — 작업 디렉터리에 기대지 않는다. */
    private fun repoRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p?.parent) { "리포 루트 탐색 실패" }
    }

    private companion object {
        /** 에 4:14 에서 절대 잘라내지 않는 절반절. */
        const val DEFENSIVE_CLAUSE = "다른 데로 말미암아 놓임과 구원을 얻으려니와"

        /** 기계가 읽는 참조 표기 — `scripture_ref` · `additional_refs`. */
        val REF = Regex("""\best-(\d+):(\d+)""")

        /** 사람이 읽는 인용 표기 — 자막 끝의 `(에 4:14)` · 범위꼴 `(에 7:3-4)`. */
        val CITATION = Regex("""\(에\s*(\d+):(\d+)(?:-(\d+))?\)""")

        /**
         * 허용된 참조 — `V20260822120000__seed_newchar_passages_gae.sql` 의 `est-*` 스물두 건.
         * 시드에 없는 절을 자막에 쓰면 `scripts/scripture_ref_check.py` 도 잡지만, 그건 저작
         * 트리를 보고 이건 **실제로 로드되는 산출물** 을 본다.
         */
        val ALLOWED = setOf(
            "2:10", "3:8", "3:9", "3:13",
            "4:1", "4:3", "4:8", "4:11", "4:13", "4:14", "4:16",
            "5:1", "5:2", "5:3", "5:4", "5:6", "5:8",
            "7:2", "7:3", "7:4", "7:6",
            "8:6",
        )

        /** 등급을 들여오는 흔한 **키** 이름들. 값(산문)에는 적용하지 않는다 — 위 (b) 주석 참조. */
        val RANK_KEYS = listOf("score", "rank", "is_correct", "correct_answer", "progress_bar", "stepper")

        /** 문서에는 있어야 하고 시나리오 값에는 없어야 하는 표기. */
        val EXCLUSION_MARKERS = listOf("에 9장", "에 7:7-10")
    }
}
