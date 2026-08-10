package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.LemuelXrApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 저작 YAML — 트랙 B `content/{인물}/scene*.yml` 과 Stage 1
 * `backend/src/main/resources/scenarios/{인물}.yml` — 의 안전 게이트가 *집행 수단* 을 갖는지 검증한다.
 *
 * 이 테스트가 존재하는 이유 — "축을 선언해 놓고 0건을 막는 게이트"(vacuous green):
 * `safety_gates[]` 에 R2(고난 가스라이팅 금지)·R3(회복 압박 금지) 축을 `id` 로 선언해 두고
 * `lint_forbidden_tokens` 가 비어 있으면, 그 게이트는 통과 로그만 남기고 실제로는
 * 아무것도 막지 않는다. 검토자는 "게이트 있음" 을 보고 넘어가고, 사용자는 무방비가 된다.
 *
 * 세 가지를 본다:
 *  1. **동기화** — 저작 토큰이 런타임 목록(`application.yml`)에 전부 반영됐는가.
 *     content/ 는 백엔드 클래스패스가 아니라 자동 동기화되지 않는다. 저작에만 적고
 *     런타임에 안 넣으면 토큰을 채워도 여전히 0건을 막는다.
 *  2. **집행 수단 보유(래칫)** — 축을 선언한 게이트는 토큰 lint 를 갖거나,
 *     토큰으로 표현할 수 없는 요건이면 `enforcement: structural` + `structural_check` 로
 *     *무엇이 집행하는지* 를 명시해야 한다. 기존 미비 게이트는 baseline 으로 동결하고
 *     **신규만 차단** 한다 (DataStandardTest 와 같은 래칫 방식).
 *  3. **자기 본문 lint** — Scene 의 사용자 노출 텍스트가 그 파일이 스스로 금지한 토큰에
 *     걸리지 않는가. scene3/scene5 헤더 주석이 "빌드 실패" 라고 적어 둔 그 검사다.
 *
 * `description`·`note` 는 검사 대상이 아니다 — 게이트 설명은 *금지 대상을 인용* 하므로
 * (예: "'하나님이 너를 도구로 다시 쓰신다'류 도구화 차단") 스캔하면 자기 자신에 걸린다.
 */
class ContentSafetyGateEnforcementTest {

    @Test
    fun `저작 게이트의 금칙 토큰이 런타임 목록에 전부 반영돼 있다`() {
        val authored = authoredTokens()
        assertThat(authored)
            .describedAs("저작 토큰이 한 건도 안 읽혔다 — content/ 경로 탐색이 깨졌다")
            .isNotEmpty()

        val runtime = runtimeTokens().toSet()
        val missing = authored.keys.filterNot { it in runtime }.sorted()

        assertThat(missing)
            .describedAs(
                "저작 YAML 에만 있고 런타임(application.yml safety.forbidden-tokens.list)에 없는 토큰이다. " +
                    "이 상태면 게이트는 문서에만 존재하고 LLM 출력은 그대로 통과한다. 선언 위치: %s",
                missing.joinToString { "$it←${authored[it]}" },
            )
            .isEmpty()
    }

    @Test
    fun `R2 R3 축을 선언한 게이트는 집행 수단을 갖는다 — 신규 vacuous 게이트 차단`() {
        val vacuous = sceneFiles().flatMap { file ->
            gatesOf(file)
                .filter { isConstraintAxis(it["id"]?.toString()) }
                .filterNot { hasEnforcement(it) }
                .map { "${relPath(file)}:${it["id"]}" }
        }.toSortedSet()

        val baselineFile = baselinePath()
        val baseline = Files.readAllLines(baselineFile, StandardCharsets.UTF_8)
            .map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()

        val introduced = vacuous.toSortedSet().apply { removeAll(baseline) }
        val fixed = baseline.toSortedSet().apply { removeAll(vacuous) }

        println("vacuous-gates: current=${vacuous.size} baseline=${baseline.size} introduced=${introduced.size} fixed=${fixed.size}")

        assertThat(introduced)
            .describedAs(
                "R2/R3 축을 id 로 선언해 놓고 집행 수단이 없는 게이트가 새로 생겼다. " +
                    "lint_forbidden_tokens 를 채우거나, 토큰으로 표현 불가한 요건이면 " +
                    "enforcement: structural + structural_check 로 무엇이 집행하는지 적을 것.",
            )
            .isEmpty()

        // 고친 항목은 baseline 에서 빼야 래칫이 조여진다. 안 빼면 다시 비워도 안 걸린다.
        assertThat(fixed)
            .describedAs(
                "집행 수단이 생긴 게이트가 baseline 에 남아 있다 — %s 에서 해당 줄을 지울 것.",
                baselineFile,
            )
            .isEmpty()
    }

    /**
     * 위 테스트의 사각지대를 메운다 — **선언조차 하지 않으면 걸리지 않는 문제**.
     *
     * `R2 R3 축을 선언한 게이트는 집행 수단을 갖는다` 는 `safety_gates[].id` 가 R2/R3 을
     * 표방한 게이트만 본다. 그래서 축을 *헤더 주석* 에만 적고 `safety_gates` 를 아예 두지
     * 않으면 그 검사의 시야 밖으로 나간다. 선언하고 안 지키면 걸리는데, 안 하면 안 걸린다.
     *
     * 이건 가정이 아니라 이미 한 번 일어난 일이다. `application.yml` 의 기록:
     * job.yml 은 "R3 — 갑절 회복을 결말로 쓰지 않는다" 를 헤더와 scene5 두 군데에 적고도
     * 토큰이 0종이라 "결국 갑절로 회복됩니다" 가 모든 게이트를 통과했다(2026-08-05 실측).
     * 그 교정은 scenarios/ 트리를 검사 대상에 *넣은* 것이었지, 산문 선언 자체를 잡는 것은
     * 아니었다 — 그래서 같은 형태가 다른 파일에 그대로 남아 있다(현재 기준선 15건).
     *
     * 실측(2026-08-11): 전역 금칙 토큰 625종 중 '부활' 을 포함한 것은 0종이다.
     * scenarios/jesus.yml 은 R3 을 "'너도 부활하라/극복하라' 압박 서사 절대 금지" 로
     * 두 군데 적어 두었지만, 아래 네 문장은 전부 통과한다.
     *   "당신도 이 무덤에서 걸어 나오셔야 합니다."
     *   "예수님이 부활하셨듯 당신도 다시 일어날 것입니다."
     *   "이제 당신 차례입니다 — 부활의 능력으로 회복하십시오."
     *   "그 슬픔을 부활의 소망으로 극복하시면 됩니다."
     * 대조군 "믿음이 부족해서 아직 회복이 없는 것입니다." 는 정상 차단된다 —
     * 스캐너가 죽은 게 아니라 이 축에 토큰이 없는 것이다.
     *
     * 래칫이지 벽이 아니다. 기존 15건은 기준선으로 동결하고 **신규만 차단** 한다.
     * 기준선에 있다는 것은 "괜찮다" 가 아니라 **"아직 산문뿐이다" 를 등재해 둔 것** 이다.
     */
    @Test
    fun `주석으로 선언한 R2 R3 축은 safety_gates 로 집행된다 — 산문 전용 선언 차단`() {
        val proseOnly = sceneFiles().flatMap { file ->
            val enforced = gatesOf(file)
                .mapNotNull { it["id"]?.toString() }
                .flatMap { id -> AXIS.findAll(id.replace('_', ' ')).map { it.groupValues[1] } }
                .toSet()
            (commentAxes(file) - enforced).map { "${relPath(file)}:R$it" }
        }.toSortedSet()

        val baselineFile = proseOnlyBaselinePath()
        val baseline = Files.readAllLines(baselineFile, StandardCharsets.UTF_8)
            .map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()

        val introduced = proseOnly.toSortedSet().apply { removeAll(baseline) }
        val fixed = baseline.toSortedSet().apply { removeAll(proseOnly) }

        println("prose-only-axes: current=${proseOnly.size} baseline=${baseline.size} introduced=${introduced.size} fixed=${fixed.size}")

        assertThat(introduced)
            .describedAs(
                "안전 축을 주석에만 적고 safety_gates 로 집행하지 않는 파일이 새로 생겼다. " +
                    "산문으로 선언된 축은 게이트가 아니다 — 같은 축 id 의 safety_gates 를 두고 " +
                    "lint_forbidden_tokens 를 채우거나, 토큰으로 표현 불가하면 " +
                    "enforcement: structural + structural_check 로 무엇이 집행하는지 적을 것.",
            )
            .isEmpty()

        assertThat(fixed)
            .describedAs(
                "집행 수단이 생긴 축이 기준선에 남아 있다 — %s 에서 해당 줄을 지워 래칫을 조일 것.",
                baselineFile,
            )
            .isEmpty()
    }

    @Test
    fun `Scene 의 사용자 노출 텍스트가 자기 파일의 금칙 토큰에 걸리지 않는다`() {
        val offenders = mutableListOf<String>()

        for (file in sceneFiles()) {
            val tokens = gatesOf(file)
                .flatMap { gate ->
                    @Suppress("UNCHECKED_CAST")
                    (gate["lint_forbidden_tokens"] as? List<Any?>).orEmpty().map { it.toString() }
                }
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            for ((path, text) in userFacingText(file)) {
                val haystack = normalize(text)
                for (t in tokens) {
                    if (haystack.contains(normalize(t))) {
                        offenders += "${relPath(file)}:$path → '$t'"
                    }
                }
            }
        }

        assertThat(offenders)
            .describedAs("Scene 본문이 그 Scene 이 스스로 금지한 표현을 담고 있다")
            .isEmpty()
    }

    @Test
    fun `structural 선언은 실제 분기 구조로 뒷받침된다 — elijah scene4 세 토로 동등성`() {
        // `enforcement: structural` 이 검사 없는 산문으로만 남으면 그것도 결국 빈 게이트다.
        // 선언(structural_check)과 같은 내용을 여기서 기계로 확인한다.
        // 요건: 외로움·소진·두려움 세 토로가 *모두 정당* 하며 단일 정답 분기가 없을 것(R3).
        val file = repoRoot().resolve("content/elijah/scene4.yml").toFile()

        val gate = gatesOf(file).single { it["id"] == "R3_all_laments_valid" }
        assertThat(gate["enforcement"]).isEqualTo("structural")

        @Suppress("UNCHECKED_CAST")
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val branch = (root["branches"] as List<Map<String, Any?>>).single { it["id"] == "br_s4_honest_lament" }
        @Suppress("UNCHECKED_CAST")
        val routes = branch["routes"] as List<Map<String, Any?>>

        // (a) 세 토로가 모두 살아 있다 — 하나라도 빠지면 그 감정은 '없는 것' 이 된다.
        val labels = routes.map { (it["when"] as Map<*, *>)["lament_label"].toString() }
        assertThat(labels).containsExactlyInAnyOrder("loneliness", "burnout", "fear")

        // (b) 셋 다 동등한 후속 경로를 가진다 — 어느 하나만 Scene 5 로 이어지면 그게 '정답' 이 된다.
        val tones = routes.map { (it["carry_to_scene5"] as Map<*, *>)["god_response_tone"].toString() }
        assertThat(tones).doesNotContainNull().doesNotHaveDuplicates().hasSize(3)
        assertThat(routes).allSatisfy { assertThat(it["card_text_ko"].toString()).isNotBlank() }

        // (c) 정답·점수·성취 표시가 없다 — 토로에 등급을 매기는 순간 R3 가 무너진다.
        //     검사 범위는 *분기 서브트리만* 이다. 파일 전체를 보면 theology 의 `disputed_points`
        //     같은 무관한 키까지 걸린다(실제로 걸렸다).
        val scoring = collectAll(branch).map { it.first }.filter { path ->
            path.split('.').any { SCORING_KEY.containsMatchIn(it.substringBefore('[')) }
        }
        assertThat(scoring)
            .describedAs("토로 분기에 점수·정답 개념이 들어왔다 — 세 토로 동등성(R3)이 깨진다")
            .isEmpty()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** 토큰 → 선언 위치(파일:게이트id). 같은 토큰이 여러 곳이면 첫 위치만 보고한다. */
    private fun authoredTokens(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (file in sceneFiles()) {
            for (gate in gatesOf(file)) {
                @Suppress("UNCHECKED_CAST")
                val tokens = (gate["lint_forbidden_tokens"] as? List<Any?>).orEmpty()
                for (t in tokens) {
                    val token = t.toString().trim()
                    if (token.isNotEmpty()) out.putIfAbsent(token, "${relPath(file)}:${gate["id"]}")
                }
            }
        }
        return out
    }

    private fun runtimeTokens(): List<String> {
        val app = moduleRoot().resolve("src/main/resources/application.yml").toFile()
        @Suppress("UNCHECKED_CAST")
        val root = app.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val safety = root["safety"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val forbidden = safety["forbidden-tokens"] as Map<String, Any?>
        // Spring 이 `List<String>` 으로 바인딩할 때와 같은 규칙 — 콤마 분리 + trim.
        return forbidden["list"].toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun gatesOf(file: File): List<Map<String, Any?>> {
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as? Map<String, Any?>
            ?: return emptyList()
        return (root["safety_gates"] as? List<Map<String, Any?>>).orEmpty()
    }

    /** id 가 R2/R3 축을 표방하는가. 표방했으면 집행 수단이 있어야 한다. */
    private fun isConstraintAxis(id: String?): Boolean {
        if (id == null) return false
        return id.startsWith("R2") || id.startsWith("R3") ||
            id.contains("instrumental") || id.contains("gaslight")
    }

    private fun hasEnforcement(gate: Map<String, Any?>): Boolean {
        val tokens = (gate["lint_forbidden_tokens"] as? List<*>).orEmpty()
        if (tokens.isNotEmpty()) return true
        val structural = gate["enforcement"]?.toString() == "structural"
        val check = gate["structural_check"]?.toString()?.isNotBlank() == true
        return structural && check
    }

    /**
     * 사용자에게 실제로 노출되는 텍스트만. `_ko` 접미 키 + 몇몇 예외.
     * `description`·`note`·`handling` 은 저작 메타라 제외한다 — 금지 대상을 인용하기 때문이다.
     */
    private fun userFacingText(file: File): List<Pair<String, String>> {
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) }
        val all = mutableListOf<Pair<String, String>>()
        collect(root, "", all)
        return all.filter { (path, _) ->
            val last = path.substringAfterLast('.').substringBefore('[')
            last.endsWith("_ko") || last in EXTRA_USER_FACING_KEYS
        }
    }

    private fun collect(node: Any?, path: String, out: MutableList<Pair<String, String>>) {
        when (node) {
            is String -> out += path to node
            is Map<*, *> -> node.forEach { (k, v) -> collect(v, if (path.isEmpty()) "$k" else "$path.$k", out) }
            is List<*> -> node.forEachIndexed { i, v -> collect(v, "$path[$i]", out) }
            else -> Unit
        }
    }

    private fun collectAll(root: Any?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        collect(root, "", out)
        return out
    }

    private fun normalize(s: String): String = s.trim().replace(WHITESPACE, " ")

    /**
     * 저작 YAML 전부 — 트랙 B `content/{인물}/scene*.yml` **과** Stage 1
     * `backend/src/main/resources/scenarios/{인물}.yml`.
     *
     * 후자는 2026-08-05 에 들어왔다. 그 전까지 이 검사는 content/ 만 걸었고, scenarios/ 의
     * 안전선은 헤더 주석으로만 존재해 *아무것도 집행하지 않았다* — job.yml 은 R3(갑절 회복
     * 금지)을 두 군데 적어 두고 토큰이 0종이라 "결국 갑절로 회복됩니다" 가 통과했다.
     * 검사 범위 밖에 있는 저작 파일은 검사 대상이 아닌 게 아니라 *무방비* 다.
     */
    private fun sceneFiles(): List<File> {
        val trackB = repoRoot().resolve("content").toFile()
        check(trackB.isDirectory) { "저작 콘텐츠 디렉터리 없음: $trackB" }
        val stage1 = moduleRoot().resolve("src/main/resources/scenarios").toFile()
        check(stage1.isDirectory) { "Stage 1 시나리오 디렉터리 없음: $stage1" }

        val b = trackB.walkTopDown()
            .filter { it.isFile && it.name.startsWith("scene") && it.name.endsWith(".yml") }
        val a = stage1.walkTopDown().filter { it.isFile && it.name.endsWith(".yml") }
        return (b + a).sortedBy { it.path }.toList()
    }

    /**
     * 주석 줄에서 표방된 R2/R3 축. YAML 파서는 주석을 버리므로 원문을 줄 단위로 읽는다.
     * 줄이 `#` 로 시작할 때만 본다 — 본문 문자열 안의 "R3" 은 선언이 아니라 인용일 수 있다.
     */
    private fun commentAxes(file: File): Set<String> =
        file.readLines(StandardCharsets.UTF_8)
            .filter { it.trimStart().startsWith("#") }
            .flatMap { line -> AXIS.findAll(line).map { it.groupValues[1] } }
            .toSet()

    private fun baselinePath(): Path =
        moduleRoot().resolve("src/test/resources/safety/vacuous-safety-gates-baseline.txt")

    private fun proseOnlyBaselinePath(): Path =
        moduleRoot().resolve("src/test/resources/safety/prose-only-axis-baseline.txt")

    private fun relPath(f: File): String =
        repoRoot().relativize(f.toPath()).toString().replace('\\', '/')

    private fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    /** LemuelXrApplication code-source 위치에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    private fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val EXTRA_USER_FACING_KEYS = setOf("static_text", "reflection_prompt")

        /** 안전 축 표기. R1(위기 라우팅)·R5(footer)는 토큰이 아니라 구조로 집행되므로 뺀다. */
        val AXIS = Regex("""\bR([23])\b""")

        /** 토로에 등급을 매기는 개념. Scene 4 분기에 하나라도 있으면 '정답' 이 생긴다. */
        val SCORING_KEY = Regex("^(score|scoring|correct|rank|ranking|best_choice|정답)$", RegexOption.IGNORE_CASE)
    }
}
