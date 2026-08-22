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
 * `scenarios/jacob.yml` 의 `R3_release_sentence_on_every_closing` 게이트를 **기계로** 확인한다.
 *
 * 이 파일이 없으면 그 게이트는 `enforcement: reviewer_only` 로 남는다 — 정직하긴 하지만
 * 아무것도 지키지 않는다. 마감 문구에서 마지막 한 문장을 지우는 편집이 **어느 검사에도
 * 걸리지 않는 상태**가 이 미션에서 가장 위험한 형태의 조용한 회귀다. 그래서 여기서 잰다.
 *
 * ── 무엇을 지키는가 ──
 * 야곱 미션의 결말은 형제의 재회다. 그 결말을 사용자의 실제 관계에 대한 *지시* 로 읽히게 두면
 * 재회가 안전하지 않은 관계(학대·폭력)에 있는 사람에게 정확히 해로운 말이 된다. 그래서 마감
 * 문구 **전건** 이 같은 해제 문장으로 끝난다 — 「이것은 야곱에게 있었던 일이며 …」.
 *
 * ── 왜 기대 문장을 이 파일에 적는가 ──
 * `DanielViolenceExclusionTest` 는 배제된 자구를 기대값으로 적지 않는다 — 잘라낸 문장이
 * 리포에 되돌아오기 때문이다. 여기는 정반대다. 이 문장은 **있어야 하는 내용** 이라
 * 두 곳에 적히는 것이 위험이 아니라 래칫이다. 파일에서 유도한 값으로만 대칭을 재면
 * 10문을 *똑같이* 약화시키는 편집이 그대로 통과한다.
 *
 * ── 대칭도 함께 잰다 ──
 * 해제 문장만 지키고 축약 경로(건너뛴 사람)의 결말이 깎이면 그것 자체가 화해 압박이다.
 * 그래서 축약 블록이 같은 마감·같은 위기 안내·같은 묵상 질문에 도달하는지, 그리고 어느
 * 경로에도 점수·순위·배지 키가 없는지를 같이 본다.
 */
class JacobReconciliationPressureTest {

    @Test
    fun `야곱 마감 문구 전건이 같은 해제 문장으로 끝난다`() {
        val closings = closingTexts()

        // 3 라벨 × 3 톤 + 라벨 없음 1 = 10. 개수를 못 박는 이유는 공집합 전칭명제 방지다 —
        // 매트릭스를 통째로 지우면 "전건이 해제 문장으로 끝난다" 가 자동으로 참이 된다.
        assertThat(closings)
            .describedAs("마감 문구가 %d건이다 — 3 라벨 × 3 톤 + 라벨 없음 1 = %d 여야 한다", closings.size, EXPECTED_CLOSINGS)
            .hasSize(EXPECTED_CLOSINGS)

        val missing = closings.filterNot { it.second.trimEnd().endsWith(RELEASE_SENTENCE) }.map { it.first }
        assertThat(missing)
            .describedAs(
                "마감 문구가 해제 문장으로 끝나지 않는다. 이 문장이 없으면 형제의 재회가 " +
                    "사용자의 실제 관계에 대한 지시로 읽힌다 — 재회가 안전하지 않은 관계가 실재한다.",
            )
            .isEmpty()

        // 라벨 3종은 Scene 3 의 선택지 id 와 정확히 같아야 한다. 어긋나면 고른 값이 어느
        // 문구에도 닿지 않고 조용히 `default` 로 떨어진다 — 화면은 멀쩡해 보인다.
        assertThat(closingLabels())
            .describedAs("Scene 5 마감 라벨이 Scene 3 선택지 id 와 어긋난다 — 고른 값이 조용히 버려진다")
            .containsExactlyInAnyOrderElementsOf(returnChoiceOptionIds())
    }

    @Test
    fun `건너뛴 사람의 결말이 깎이지 않는다 — 경로 대칭`() {
        val scene5 = scenes().single { it["id"] == 5 }

        @Suppress("UNCHECKED_CAST")
        val extras = scene5["extras"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val warning = scene5["trigger_warning"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val blocks = extras["conditional_blocks"] as List<Map<String, Any?>>

        val destination = warning["skip_alternative_scene_id"]?.toString()
        val block = blocks.singleOrNull { it["id"] == destination }
        assertThat(block)
            .describedAs("건너뛰기 목적지 '%s' 에 해당하는 축약 블록이 없다", destination)
            .isNotNull()
        requireNotNull(block)

        @Suppress("UNCHECKED_CAST")
        val renders = (block["renders"] as? List<String>).orEmpty()
        assertThat(renders)
            .describedAs(
                "축약 경로가 정본 경로와 같은 마감에 도달하지 않는다. 재회 연출을 건너뛴 사람의 " +
                    "결말이 깎이면 그것이 곧 화해 압박이다.",
            )
            .contains("closing_texts", "crisis_reminder", "value_prompt")

        // 정본 경로가 그 셋을 실제로 갖고 있어야 `renders` 선언이 의미를 갖는다.
        assertThat(extras).containsKeys("closing_texts", "crisis_reminder")
        assertThat(scene5["value_prompt"]?.toString()).isNotBlank()
    }

    @Test
    fun `야곱 시나리오 어디에도 점수·순위 키가 없다`() {
        // Scene 3 의 세 선택지는 동등하다. 어느 경로에도 우열 신호를 붙이지 않는다 —
        // 붙는 순간 「돌아가는 것이 정답」이 되고, 그것이 이 미션이 금하는 바로 그 압박이다.
        val offenders = scalars()
            .map { it.first }
            .filter { path -> RANKING_KEYS.any { path.substringAfterLast('.').substringBefore('[').equals(it, true) } }
            .sorted()

        assertThat(offenders)
            .describedAs("우열 신호 키가 들어왔다 — 세 선택지와 두 예물 응대는 등급이 없다")
            .isEmpty()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun closingTexts(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for ((label, node) in closingMatrix()) {
            when (node) {
                is String -> out += label to node
                is Map<*, *> -> node.forEach { (tone, text) -> out += "$label.$tone" to text.toString() }
                else -> Unit
            }
        }
        return out
    }

    /** 톤 분기를 가진 라벨만 — `default`(라벨 없음)는 Scene 3 선택지가 아니다. */
    private fun closingLabels(): List<String> =
        closingMatrix().filterValues { it is Map<*, *> }.keys.map { it.toString() }

    private fun closingMatrix(): Map<String, Any?> {
        val scene5 = scenes().single { it["id"] == 5 }

        @Suppress("UNCHECKED_CAST")
        val extras = scene5["extras"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val matrix = extras["closing_texts"] as? Map<String, Any?>
        assertThat(matrix).describedAs("Scene 5 에 closing_texts 가 없다").isNotNull()
        return requireNotNull(matrix)
    }

    private fun returnChoiceOptionIds(): List<String> {
        val scene3 = scenes().single { it["id"] == 3 }

        @Suppress("UNCHECKED_CAST")
        val extras = scene3["extras"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val options = extras["options"] as? List<Map<String, Any?>>
        assertThat(options).describedAs("Scene 3 에 선택지가 없다 — 이 미션의 유일한 사용자 선택이다").isNotNull()
        return requireNotNull(options).map { it["id"].toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun scenes(): List<Map<String, Any?>> = root()["scenes"] as List<Map<String, Any?>>

    private fun root(): Map<String, Any?> {
        val url = requireNotNull(javaClass.classLoader.getResource("scenarios/jacob.yml")) {
            "클래스패스에 scenarios/jacob.yml 이 없다"
        }

        @Suppress("UNCHECKED_CAST")
        return File(url.toURI()).reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
    }

    /** `safety_gates` 서브트리는 뺀다 — 금지 대상을 인용하는 자리라 자기 자신에 걸린다. */
    private fun scalars(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for ((k, v) in root()) {
            if (k == "safety_gates") continue
            collect(v, k, out)
        }
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

    @Suppress("unused")
    private fun repoRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p?.parent) { "리포 루트 탐색 실패" }
    }

    private companion object {
        /**
         * 3 라벨 × 3 톤 + 라벨 없음 1. 라벨 없음에 톤 분기가 없는 것은 누락이 아니라 저작의
         * 결정이다 — 고르지 않은 사람에게 톤을 물어본 적이 없으므로 지어낼 근거가 없다.
         */
        const val EXPECTED_CLOSINGS = 10

        /** 마감 문구 전건의 마지막 문장. 저작 정본은 `content/jacob/scene5.yml`. */
        const val RELEASE_SENTENCE = "이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 된다."

        /** 우열 신호로 읽히는 키. 하나라도 붙으면 세 선택지에 등급이 생긴다. */
        val RANKING_KEYS = listOf("score", "rank", "ranking", "badge", "recommended", "grade", "weight")
    }
}
