package github.lms.lemuel.xr.game.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 래칫 — 런타임 시나리오(`resources/scenarios` 의 yml)에 상담 번호를 *하드코딩* 하지 못하게 막는다.
 *
 * 이 테스트가 존재하는 이유: 2024-01-01 자살예방 상담이 109 로 통합됐는데도 이 yml 들은
 * 오랫동안 1393 을 그대로 들고 있었고, 그 문구는 위기 상태의 사용자가 직접 읽는 카피였다.
 * 번호를 값으로 박아 두면 다음 개정 때 같은 일이 반복된다 —
 * 그래서 값에는 `{{crisis_resources.default}}` 토큰만 허용하고, 실제 번호는
 * [CrisisTokenResolver] 가 `crisis_resources` 정본에서 런타임에 주입한다.
 *
 * 스캔 대상은 *파싱된 값* 이다(주석 제외) — 사용자에게 나가는 건 값뿐이다.
 * [ForbiddenTokenConfigTest] 와 같은 성격의 게이트다.
 */
class ScenarioHotlineRatchetTest {

    @Test
    fun `시나리오 yml 값에 상담 번호가 하드코딩돼 있지 않다`() {
        val offenders = mutableListOf<String>()

        for (file in scenarioFiles()) {
            for ((path, text) in stringsOf(file)) {
                val hits = FORBIDDEN.filter { it.containsMatchIn(text) }
                if (hits.isNotEmpty()) {
                    offenders += "${file.name}:$path → ${hits.joinToString { it.pattern }}"
                }
            }
        }

        assertThat(offenders)
            .describedAs(
                "시나리오에 상담 번호가 값으로 박혀 있다. 번호 대신 %s 토큰을 쓸 것 — " +
                    "실제 번호는 crisis_resources 정본에서 주입된다.",
                CrisisTokenResolver.TOKEN,
            )
            .isEmpty()
    }

    @Test
    fun `위기 문구가 사라지지 않았다 — 최소 한 씬은 위기 연락처 토큰을 갖는다`() {
        // 래칫을 "번호를 지우면 통과" 로 우회하지 못하게 한다(R1 안전선은 제거 금지).
        val withToken = scenarioFiles().filter { file ->
            stringsOf(file).any { (_, text) -> text.contains(CrisisTokenResolver.TOKEN) }
        }.map { it.name }

        assertThat(withToken)
            .describedAs("모든 인물 시나리오에 위기 연락처 토큰이 있어야 한다 (R1)")
            .hasSameElementsAs(scenarioFiles().map { it.name })
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun scenarioFiles(): List<File> {
        val url = requireNotNull(javaClass.classLoader.getResource(SCENARIO_DIR)) {
            "클래스패스에 $SCENARIO_DIR 가 없다"
        }
        val files = File(url.toURI()).listFiles { f: File -> f.name.endsWith(".yml") }
        return requireNotNull(files) { "$SCENARIO_DIR 를 읽을 수 없다" }.sortedBy { it.name }
    }

    /** yml 을 파싱해 (경로, 문자열값) 쌍을 모두 뽑는다. 주석은 파서가 버린다. */
    private fun stringsOf(file: File): List<Pair<String, String>> {
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) }
        val out = mutableListOf<Pair<String, String>>()
        collect(root, "", out)
        return out
    }

    private fun collect(node: Any?, path: String, out: MutableList<Pair<String, String>>) {
        when (node) {
            is String -> out += path to node
            is Map<*, *> -> node.forEach { (k, v) -> collect(v, if (path.isEmpty()) "$k" else "$path.$k", out) }
            is List<*> -> node.forEachIndexed { i, v -> collect(v, "$path[$i]", out) }
            else -> Unit
        }
    }

    companion object {
        private const val SCENARIO_DIR = "scenarios"

        /**
         * 하드코딩 금지 번호.
         *
         * 1577-0199 · 1388 · 1366 은 *폐지된 번호가 아니다* — 각자 담당 분야 상담을 계속한다.
         * 여기서 막는 건 "폐지" 가 아니라 "시나리오 값에 번호를 직접 박는 것" 이다.
         * 어느 번호를 어떤 이름으로 안내할지는 `crisis_resources` 카탈로그 한 곳에서 정한다.
         */
        private val FORBIDDEN = listOf(
            Regex("""(?<!\d)1393(?!\d)"""),
            Regex("""1577-0199"""),
            Regex("""1588-9191"""),
            Regex("""(?<!\d)1388(?!\d)"""),
            Regex("""(?<!\d)1366(?!\d)"""),
        )
    }
}
