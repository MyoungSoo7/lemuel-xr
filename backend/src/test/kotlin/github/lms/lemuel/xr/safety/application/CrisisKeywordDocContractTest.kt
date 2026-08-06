package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.LemuelXrApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * **문서(정본) ↔ 런타임(사본) 양방향 일치 검사.**
 *
 * 2026-08-06 사용자 결정 "정본을 문서로" 를 집행한다. 정본은 `docs/EMOTION-CLASSIFIER.md` §3 의
 * `CRISIS_KEYWORDS_*` 사전이고, 런타임은 `application.yml` 의 `safety.crisis-keywords-regex` 다.
 *
 * 이 테스트가 없으면 그 결정은 문장으로만 남는다. 실제로 두 곳은 오랫동안 갈라져 있었다 —
 * 문서에는 3단 등급과 18개 키워드가 있었고 런타임에는 6개 키워드가 전부 같은 등급이었다.
 * "정본" 이라는 말은 *한쪽만 고치는 길이 없을 때* 만 뜻을 가진다.
 *
 * 두 방향 다 본다:
 *  1. **문서 → 런타임** — 문서 사전의 모든 키워드가 문서가 말한 등급으로 실제 판정되는가.
 *     문자열 비교가 아니라 스캐너를 *돌려서* 확인한다. 정규식 문법이 달라도(`죽고\s?싶` 하나가
 *     `죽고 싶`·`죽고싶` 둘을 덮는 식) 행동이 같으면 통과여야 하기 때문이다.
 *  2. **런타임 → 문서** — 정규식의 리터럴 대안이 전부 문서에 적혀 있는가. 이 방향이 없으면
 *     런타임에만 몰래 추가된 키워드가 문서를 정본이라 부르면서도 문서 밖에서 산다.
 *
 * 오탐도 여기서 *고정* 한다. medium 사전은 일상어와 구분되지 않는 항목을 포함하기로 한
 * 상태이고(문서 §3 "알려진 한계"), 그 사실을 테스트로 못 박아 두지 않으면 다음 사람이
 * "우연히 통과 중" 인지 "의도적으로 허용 중" 인지 구별할 수 없다.
 */
class CrisisKeywordDocContractTest {

    private val regexText = runtimeRegex()
    private val scanner = CrisisKeywordScanner(regexText)

    // ───────────────────────── 1. 문서 → 런타임 ─────────────────────────

    @Test
    fun `문서 사전의 모든 키워드가 문서가 말한 등급으로 판정된다`() {
        val dictionary = docDictionary()

        assertThat(dictionary)
            .describedAs("문서 사전이 한 건도 안 읽혔다 — docs/EMOTION-CLASSIFIER.md 파싱이 깨졌다")
            .isNotEmpty()

        val wrong = dictionary.mapNotNull { (keyword, expected) ->
            val actual = scanner.scan(keyword).severity
            if (actual == expected) null else "$keyword: 문서=$expected 런타임=${actual ?: "매칭없음"}"
        }

        assertThat(wrong)
            .describedAs(
                "문서(정본)에 적힌 등급과 런타임 판정이 다르다. 문서에만 적고 " +
                    "application.yml 의 crisis-keywords-regex 를 안 고치면 여기서 걸린다.",
            )
            .isEmpty()
    }

    @Test
    fun `문서 사전은 세 등급이 모두 비어 있지 않다`() {
        // 파싱이 조용히 한 등급만 읽어 오면 위 테스트가 vacuous green 이 된다.
        val byTier = docDictionary().values.groupingBy { it }.eachCount()

        assertThat(byTier.keys).containsExactlyInAnyOrder("critical", "high", "medium")
        assertThat(byTier.values).allSatisfy { assertThat(it).isGreaterThan(1) }
    }

    // ───────────────────────── 2. 런타임 → 문서 ─────────────────────────

    @Test
    fun `정규식의 리터럴 대안은 전부 문서에 적혀 있다`() {
        val doc = docDictionary()
        val missing = mutableListOf<String>()

        runtimeAlternatives().forEach { (alternative, tier) ->
            if (alternative in REGEX_FORM_ALTERNATIVES) return@forEach
            expandOptionalSpace(alternative).forEach { form ->
                if (doc[form] != tier) missing += "$form(런타임 $tier)"
            }
        }

        assertThat(missing)
            .describedAs(
                "런타임 정규식에만 있고 문서 사전에는 그 등급으로 없는 키워드다. " +
                    "정본이 문서라면 런타임에 추가할 때 문서를 먼저 고쳐야 한다.",
            )
            .isEmpty()
    }

    @Test
    fun `정규식 형태로만 쓰는 대안은 문서에 그 형태가 인용돼 있다`() {
        // 리터럴로 옮길 수 없는 대안(선행 문맥 요구)은 면제 목록에 두되, 면제가 조용해지지
        // 않도록 문서에 정규식 원문이 인용돼 있는지 확인한다.
        val alternatives = runtimeAlternatives().map { it.first }
        assertThat(alternatives)
            .describedAs("면제 목록이 실제 정규식과 어긋났다 — 면제만 남고 대상은 사라졌다")
            .containsAll(REGEX_FORM_ALTERNATIVES)

        assertThat(docText())
            .describedAs("문서가 고인 지시어 목록을 인용하지 않으면 런타임이 조용히 바뀔 수 있다")
            .contains("(?:먼저 간|떠난|돌아가신|죽은|고인)")
        assertThat(docText())
            .describedAs("문서가 소멸 가정 목록을 인용하지 않으면 런타임이 조용히 바뀔 수 있다")
            .contains("(?:없어져도|사라져도|죽어도)")
        // 앵커가 조용히 빠지면(= 맨 "목매" 가 되면) 비유 오탐이 쏟아진다. 앵커 자체를 문서에 못박는다.
        assertThat(docText())
            .describedAs("문서가 1인칭 재귀 앵커를 인용하지 않으면 앵커가 조용히 사라질 수 있다")
            .contains("(?:스스로|혼자서?)[^\\n]{0,4}목을\\s?(?:매|맬|맸)")
    }

    // ───────────────────────── 3. 사람 결정 고정 ─────────────────────────

    @Test
    fun `사별 4종의 등급은 2026-08-06 사용자 결정 그대로다`() {
        // 이 4문장은 룻(사별) 미션 릴리스를 막고 있던 미결 항목이었고, 등급은 사람이 정했다.
        // 코드가 바뀌어 등급이 흔들리면 그건 결정을 뒤집는 것이므로 여기서 멈춰야 한다.
        assertThat(scanner.scan("나도 따라 죽고 싶어요").severity).isEqualTo("critical")
        assertThat(scanner.scan("같이 묻히고 싶다").severity).isEqualTo("high")
        assertThat(scanner.scan("그 사람 곁으로 가고 싶어").severity).isEqualTo("medium")
        assertThat(scanner.scan("먼저 간 사람 곁에 있고 싶어요").severity).isEqualTo("medium")
    }

    @Test
    fun `곁에 있고 싶 은 고인 지시어가 앞에 있을 때만 잡힌다`() {
        // 단독 토큰으로 두면 일상어를 전부 삼킨다. 과차단은 안전이 아니라 손실이다.
        assertThat(scanner.scan("가족 곁에 있고 싶어요").matched).isFalse()
        assertThat(scanner.scan("아이 곁에 있고 싶어서 재택으로 바꿨어요").matched).isFalse()

        assertThat(scanner.scan("돌아가신 아버지 곁에 있고 싶어요").severity).isEqualTo("medium")
    }

    @Test
    fun `등급 도입 이전부터 있던 키워드는 전부 critical 로 남는다`() {
        // 3단 등급 이전 런타임은 어떤 매칭이든 LLM 을 건너뛰고 위기 화면을 강제했다.
        // 그 동작에 해당하는 등급은 critical 이다 — 하나라도 내려가면 그건 완화다.
        listOf("자살", "죽고 싶", "죽고싶", "죽어 버", "뛰어내리", "자해").forEach {
            assertThat(scanner.scan(it).severity)
                .describedAs("등급 도입 전부터 있던 키워드 '%s' 의 보호가 약해졌다", it)
                .isEqualTo("critical")
        }
    }

    @Test
    fun `영문 키워드는 대문자로 써도 잡힌다`() {
        // 사용자는 문장 첫머리를 대문자로 쓴다. 소문자 사전만 두면 그대로 샌다.
        assertThat(scanner.scan("I want to kill myself").severity).isEqualTo("critical")
        assertThat(scanner.scan("I can't go on").severity).isEqualTo("high")
    }

    // ───────────────── 4. medium 좁히기 (2026-08-06) — 양쪽을 다 잰다 ─────────────────

    @Test
    fun `좁히기 이전에 오탐이던 일상어가 이제 안 걸린다`() {
        // 이 세 문장은 좁히기 전까지 전부 medium 이었다(단독 토큰 `마지막`·`혼자 살아`·
        // `아무도 모를`). 사용자 결정으로 뺐으므로, 다시 걸리면 되돌아간 것이다.
        listOf(
            "마지막 장면이 좋았어요",
            "혼자 살아온 지 십 년이에요",
            "그건 아무도 모를 거예요",
            "마지막으로 정리하겠습니다",
        ).forEach {
            assertThat(scanner.scan(it).matched)
                .describedAs("좁히기가 되돌아갔다 — 일상어 '%s' 가 다시 걸린다", it)
                .isFalse()
        }
    }

    @Test
    fun `좁히면서 위험 형태까지 같이 잘라내지는 않았다`() {
        // 위 테스트만 있으면 riskSignal 을 통째로 비워도 초록이다. 좁히기의 실패 모드는
        // 오탐이 아니라 *미탐* 이므로, 남기기로 한 형태를 여기서 못 박는다.
        listOf(
            "유서를 써 뒀어요",
            "마지막 인사를 하고 싶어요",
            "마지막 편지를 남깁니다",
            "저 혼자 살아남았어요",
            "이제 혼자 살아 뭐하나 싶어요",
            "제가 죽어도 아무도 모를 거예요",
            "아무도 모르게 떠나고 싶어요",
        ).forEach {
            assertThat(scanner.scan(it).severity)
                .describedAs("좁히기가 과했다 — 위험 형태 '%s' 를 놓친다", it)
                .isEqualTo("medium")
        }

        // 비용의 상한 — 오탐이 남더라도 흐름을 끊지 않는다(medium 은 lockout 이 아니다).
        assertThat(CrisisKeywordScanner.PATTERN_TO_SEVERITY["risk_signal"]).isEqualTo("medium")
    }

    @Test
    fun `평범한 문장은 어떤 등급에도 걸리지 않는다`() {
        listOf(
            "오늘 하루도 버텨냈어요.",
            "아버지 기일에 성묘를 다녀왔습니다.",
            "요즘 잠이 잘 안 옵니다.",
        ).forEach {
            assertThat(scanner.scan(it).matched)
                .describedAs("과차단 — '%s'", it)
                .isFalse()
        }
    }

    // ───────────────────────── 파싱 헬퍼 ─────────────────────────

    /** `application.yml` 의 placeholder 기본값 — `${'$'}{SAFETY_CRISIS_REGEX:...}` 안쪽. */
    private fun runtimeRegex(): String {
        val app = moduleRoot().resolve("src/main/resources/application.yml").toFile()
        @Suppress("UNCHECKED_CAST")
        val root = app.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val safety = root["safety"] as Map<String, Any?>
        val raw = safety["crisis-keywords-regex"].toString()

        val open = raw.indexOf(':')
        check(raw.startsWith("\${") && raw.endsWith("}") && open > 0) {
            "crisis-keywords-regex 가 \${ENV:default} 형태가 아니다: $raw"
        }
        return raw.substring(open + 1, raw.length - 1)
    }

    /** 문서 §3 의 `CRISIS_KEYWORDS_*` 사전 → `키워드 → 등급`. */
    private fun docDictionary(): Map<String, String> {
        val text = docText()
        return DOC_LISTS.flatMap { (listName, tier) ->
            val body = Regex("$listName\\s*=\\s*\\[(.*?)\\n]", RegexOption.DOT_MATCHES_ALL)
                .find(text)
                ?.groupValues
                ?.get(1)
                ?: error("문서에서 $listName 을 찾지 못했다 — docs/EMOTION-CLASSIFIER.md §3 구조가 바뀌었다")
            // 주석 줄에 예시 문장이 따옴표로 들어 있어(예: `# ("같이 묻히고 싶다")`) 먼저 걷어낸다.
            val stripped = body.lines().joinToString("\n") { it.substringBefore('#') }
            Regex("\"([^\"]+)\"").findAll(stripped).map { it.groupValues[1] to tier }
        }.toMap()
    }

    /** 정규식의 명명 그룹을 풀어 `(대안, 등급)` 목록으로. 그룹 밖 `|` 는 그룹 구분자다. */
    private fun runtimeAlternatives(): List<Pair<String, String>> =
        Regex("\\(\\?<([A-Za-z]+)>").findAll(regexText).map { m ->
            val group = m.groupValues[1]
            val pattern = CrisisKeywordScanner.GROUP_TO_PATTERN[group]
                ?: error("정규식 그룹 '$group' 이 GROUP_TO_PATTERN 에 없다 — 조용히 crisis_unclassified 로 떨어진다")
            val tier = CrisisKeywordScanner.PATTERN_TO_SEVERITY.getValue(pattern)
            splitTopLevel(groupBody(regexText, m.range.last + 1)).map { it to tier }
        }.flatten().toList()

    /** `open` 위치(그룹 여는 괄호 바로 다음)부터 짝이 맞는 닫는 괄호 직전까지. */
    private fun groupBody(regex: String, open: Int): String {
        var depth = 1
        var i = open
        while (i < regex.length) {
            when (regex[i]) {
                '\\' -> i++
                '(' -> depth++
                ')' -> if (--depth == 0) return regex.substring(open, i)
            }
            i++
        }
        error("정규식 그룹 괄호가 안 닫혔다")
    }

    /** 괄호·대괄호 밖의 `|` 로만 나눈다. 중첩 `(?:a|b)` 안의 `|` 는 대안 구분자가 아니다. */
    private fun splitTopLevel(body: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var paren = 0
        var bracket = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' -> { buf.append(c); if (i + 1 < body.length) buf.append(body[++i]) }
                c == '[' -> { bracket++; buf.append(c) }
                c == ']' -> { bracket--; buf.append(c) }
                c == '(' -> { paren++; buf.append(c) }
                c == ')' -> { paren--; buf.append(c) }
                c == '|' && paren == 0 && bracket == 0 -> { out += buf.toString(); buf.clear() }
                else -> buf.append(c)
            }
            i++
        }
        out += buf.toString()
        return out.filter { it.isNotBlank() }
    }

    /** `죽고\s?싶` → {`죽고 싶`, `죽고싶`}. 두 형태 모두 문서에 있어야 한다. */
    private fun expandOptionalSpace(alternative: String): List<String> {
        if (!alternative.contains(OPTIONAL_SPACE)) return listOf(alternative)
        val parts = alternative.split(OPTIONAL_SPACE)
        return listOf(parts.joinToString(" "), parts.joinToString(""))
    }

    private fun docText(): String =
        Files.readString(repoRoot().resolve("docs/EMOTION-CLASSIFIER.md"), StandardCharsets.UTF_8)

    private fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    /** LemuelXrApplication code-source 위치에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    private fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }

    private companion object {
        const val OPTIONAL_SPACE = "\\s?"

        val DOC_LISTS = listOf(
            "CRISIS_KEYWORDS_CRITICAL" to "critical",
            "CRISIS_KEYWORDS_HIGH" to "high",
            "CRISIS_KEYWORDS_MEDIUM" to "medium",
        )

        /**
         * 리터럴로 옮길 수 없는 대안 — 선행 문맥을 요구하는 형태. 문서에는 대표 예문
         * ("먼저 간 사람 곁에 있고 싶")으로 적혀 있고 정규식 원문은 주석으로 인용돼 있다.
         * 이 목록에 넣는 것 자체가 결정이므로 조용히 늘어나면 안 된다 —
         * `정규식 형태로만 쓰는 대안은 문서에 그 형태가 인용돼 있다` 가 짝을 맞춘다.
         */
        val REGEX_FORM_ALTERNATIVES = listOf(
            "(?:먼저 간|떠난|돌아가신|죽은|고인)[^\\n]{0,12}곁에 있고 싶",
            "(?:없어져도|사라져도|죽어도)[^\\n]{0,8}아무도 모를",
            // 1인칭 재귀 문맥을 요구한다 — 앵커를 떼면 "성적에 목매다" 류 비유가 전부 걸린다.
            // 문서에는 대표 리터럴 4종("스스로 목을 매" 등)으로 적혀 있고 원문은 주석에 인용돼 있다.
            "(?:스스로|혼자서?)[^\\n]{0,4}목을\\s?(?:매|맬|맸)",
        )
    }
}
