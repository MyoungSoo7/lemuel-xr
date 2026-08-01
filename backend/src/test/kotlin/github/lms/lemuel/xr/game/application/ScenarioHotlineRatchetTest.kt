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
 * **문자열만 보면 안 된다**: yml 의 `hotline: 1393` 은 따옴표가 없으면 Integer 로 파싱되고,
 * [CrisisTokenResolver.substitute] 는 비문자열을 그대로 통과시키므로 그 숫자는 JSON number
 * 로 클라이언트에 도달한다. 그래서 [collect] 는 숫자 노드도 문자열화해 함께 수집한다.
 * [ForbiddenTokenConfigTest] 와 같은 성격의 게이트다.
 */
class ScenarioHotlineRatchetTest {

    @Test
    fun `시나리오 yml 값에 상담 번호가 하드코딩돼 있지 않다`() {
        val offenders = mutableListOf<String>()

        for (file in scenarioFiles()) {
            for ((path, text) in scalarsOf(file)) {
                // 토큰 자체는 번호가 아니다 — 토큰을 걷어낸 나머지만 검사한다.
                val stripped = text.replace(CrisisTokenResolver.TOKEN, "")
                val hits = FORBIDDEN.filter { it.containsMatchIn(stripped) }
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
    fun `위기 문구가 사라지지 않았다 — 모든 시나리오가 토큰을 갖고, 번호꼴 값은 전부 토큰이다`() {
        // (a) 래칫을 "번호를 지우면 통과" 로 우회하지 못하게 한다(R1 안전선은 제거 금지).
        val withToken = scenarioFiles().filter { file ->
            scalarsOf(file).any { (_, text) -> text.contains(CrisisTokenResolver.TOKEN) }
        }.map { it.name }

        assertThat(withToken)
            .describedAs("모든 인물 시나리오에 위기 연락처 토큰이 있어야 한다 (R1)")
            .hasSameElementsAs(scenarioFiles().map { it.name })

        // (b) 토큰을 *하나만* 남기고 나머지를 하드코딩하는 우회를 막는다.
        //     위기 문맥 키(crisis_*/hotline/상담 …) 아래의 값에는 토큰 외의 숫자가 있을 수 없다.
        //     — 알려진 번호든(FORBIDDEN) 아직 모르는 새 번호든 똑같이 걸린다.
        val digitsInCrisisCopy = mutableListOf<String>()
        // (c) 파일 어디에 있든 *값 전체가 전화번호 꼴* 이면 하드코딩이다.
        //     시나리오의 정상 숫자값은 duration_sec(≤120)·id·weight 뿐이라 이 꼴과 겹치지 않는다.
        val phoneShaped = mutableListOf<String>()

        for (file in scenarioFiles()) {
            for ((path, text) in scalarsOf(file)) {
                val stripped = text.replace(CrisisTokenResolver.TOKEN, "")
                if (CRISIS_KEY.containsMatchIn(path) && stripped.any { it.isDigit() }) {
                    digitsInCrisisCopy += "${file.name}:$path → $text"
                }
                if (PHONE_SHAPED.matches(text.trim())) {
                    phoneShaped += "${file.name}:$path → $text"
                }
            }
        }

        assertThat(digitsInCrisisCopy)
            .describedAs(
                "위기 문맥 값에 숫자가 직접 들어 있다. 위기 연락처는 %s 토큰으로만 표현한다 — " +
                    "토큰 하나만 남기고 나머지를 번호로 박는 우회를 막는다.",
                CrisisTokenResolver.TOKEN,
            )
            .isEmpty()

        assertThat(phoneShaped)
            .describedAs(
                "값 전체가 전화번호 꼴이다. 상담 번호는 값이 아니라 %s 토큰이어야 한다.",
                CrisisTokenResolver.TOKEN,
            )
            .isEmpty()
    }

    @Test
    fun `ScenePayloadAssembler 를 우회하는 키에는 위기 토큰을 두지 않는다`() {
        // 회귀 가드 — 이 키들의 값은 ScenePayloadAssembler(=치환 지점)를 통과하지 않고
        // 클라이언트로 나간다:
        //   value_prompt · linked_values → CompleteGameSessionUseCase → GameController.complete()
        //   monologues · outcomes · reactions → ResponseResolver → DecideSceneUseCase.responseText
        // 지금은 이 키들에 토큰이 없어서 실제 누출은 없다. 누가 여기에 토큰을 넣는 순간
        // 사용자는 치환되지 않은 `{{crisis_resources.default}}` 원문을 읽게 된다 —
        // 조용한 누출 대신 빨간 빌드로 바꾼다.
        val offenders = mutableListOf<String>()

        for (file in scenarioFiles()) {
            for ((path, text) in scalarsOf(file)) {
                val segments = path.split('.').map { it.substringBefore('[') }
                if (segments.none { it in BYPASS_KEYS }) continue
                if (text.contains(TOKEN_PREFIX)) {
                    offenders += "${file.name}:$path → $text"
                }
            }
        }

        assertThat(offenders)
            .describedAs(
                "%s 키의 값은 ScenePayloadAssembler 를 거치지 않는다 — 여기에 %s* 토큰을 두면 " +
                    "치환되지 않은 원문이 사용자에게 나간다. 위기 안내는 Scene extras(치환 경로)에 둘 것.",
                BYPASS_KEYS, TOKEN_PREFIX,
            )
            .isEmpty()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun scenarioFiles(): List<File> {
        val url = requireNotNull(javaClass.classLoader.getResource(SCENARIO_DIR)) {
            "클래스패스에 $SCENARIO_DIR 가 없다"
        }
        val files = File(url.toURI()).listFiles { f: File -> f.name.endsWith(".yml") }
        return requireNotNull(files) { "$SCENARIO_DIR 를 읽을 수 없다" }.sortedBy { it.name }
    }

    /** yml 을 파싱해 (경로, 스칼라값의 문자열 표현) 쌍을 모두 뽑는다. 주석은 파서가 버린다. */
    private fun scalarsOf(file: File): List<Pair<String, String>> {
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) }
        val out = mutableListOf<Pair<String, String>>()
        collect(root, "", out)
        return out
    }

    private fun collect(node: Any?, path: String, out: MutableList<Pair<String, String>>) {
        when (node) {
            is String -> out += path to node
            // 따옴표 없는 `hotline: 1393` 은 Integer 로 파싱된다 — 문자열만 보면 스캐너에 안 보인다.
            is Number -> out += path to node.toString()
            is Map<*, *> -> node.forEach { (k, v) -> collect(v, if (path.isEmpty()) "$k" else "$path.$k", out) }
            is List<*> -> node.forEachIndexed { i, v -> collect(v, "$path[$i]", out) }
            else -> Unit
        }
    }

    companion object {
        private const val SCENARIO_DIR = "scenarios"

        /** `{{crisis_resources.` — default 외의 다른 위기 토큰이 생겨도 잡히게 접두사로 본다. */
        private const val TOKEN_PREFIX = "{{crisis_resources."

        /**
         * 하드코딩 금지 번호.
         *
         * 1577-0199 · 1388 · 1366 은 *폐지된 번호가 아니다* — 각자 담당 분야 상담을 계속한다.
         * 여기서 막는 건 "폐지" 가 아니라 "시나리오 값에 번호를 직접 박는 것" 이다.
         * 어느 번호를 어떤 이름으로 안내할지는 `crisis_resources` 카탈로그 한 곳에서 정한다.
         *
         * 현행 정번호 109 도 포함한다 — "지금 맞는 번호니까 박아도 된다" 가 바로 1393 을
         * 남긴 논리였다. 자릿수 경계(`(?<!\d)…(?!\d)`)로 `pp.109-115`·`duration: 1091` 같은
         * 오탐은 피한다.
         */
        private val FORBIDDEN = listOf(
            Regex("""(?<!\d)109(?!\d)"""),
            Regex("""(?<!\d)1393(?!\d)"""),
            Regex("""1577-0199"""),
            Regex("""1588-9191"""),
            Regex("""(?<!\d)1388(?!\d)"""),
            Regex("""(?<!\d)1366(?!\d)"""),
        )

        /** 위기 문맥 키 — 이 아래의 값은 토큰 외에 숫자를 가질 수 없다. */
        private val CRISIS_KEY = Regex("""crisis|hotline|helpline|counsel|emergency|상담|위기""", RegexOption.IGNORE_CASE)

        /**
         * 값 *전체* 가 전화번호 꼴인지. 산문 속 숫자(`시편 119편`·`450명`·`창 41:34`)를 오탐하지
         * 않도록 부분일치가 아니라 완전일치만 본다.
         * - `1577-0199` · `1588-9191` 같은 국번형
         * - `1393` · `1366` 같은 1로 시작하는 4자리 특수번호
         * 시나리오의 정상 숫자값(id·duration_sec ≤120·weight)은 어느 쪽에도 걸리지 않는다.
         */
        private val PHONE_SHAPED = Regex("""\d{3,4}-\d{3,4}(-\d{4})?|1\d{3}""")

        /** ScenePayloadAssembler(치환 지점)를 통과하지 않고 클라이언트로 나가는 yml 키. */
        private val BYPASS_KEYS = setOf(
            "value_prompt",
            "linked_values",
            "monologues",
            "outcomes",
            "reactions",
        )
    }
}
