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
 * `scenarios/daniel.yml` 의 `violence_exclusion_dan_6_24` 게이트를 **기계로** 확인한다.
 *
 * 그 게이트는 `enforcement: structural` 이고, structural 게이트는 `verified_by` 로
 * 실재하는 테스트를 지목해야 한다(ContentSafetyGateEnforcementTest 의 매달린 참조 차단).
 * 이 파일이 그 지목 대상이다 — 없으면 게이트는 산문 두 문장일 뿐이다.
 *
 * ── 무엇을 배제했나 ──
 * 단 6:24 이하(참소자와 그 가족의 집단 처형, 그리고 그 뒤의 조서)는 이 미션에 들어오지
 * 않는다. `content/daniel/README.md` 의 결정이고 인물 전 Scene 공통이다. 미션의 종료점은
 * 6:23 이다.
 *
 * ── 왜 「자구 목록」으로 재지 않는가 ──
 * 배제된 문장을 기대값으로 적으면 그 문장이 리포에 다시 들어온다. `extras` 는 payload 로
 * 통째 복사되므로 저작에서 잘라낸 문장을 어딘가에 적어 두면 그게 화면으로 나갈 길이 생긴다.
 * 그래서 **절 번호의 상한** 만 잰다 — 자구를 옮기지 않고도 "6:23 이후가 없다" 를 판정할 수 있다.
 *
 * ── 검사 범위의 함정 두 개 ──
 *  1. `safety_gates` 서브트리는 제외한다. 그 게이트의 `axis` 자체가 "단 6:24 이하 완전 배제"
 *     라서, 파일 전체를 훑으면 **배제를 선언한 문장이 배제 위반으로 잡힌다.**
 *  2. 인용 표기는 괄호꼴(`(단 6:23)`)만 성구로 센다. 주석·메모는 `단 6:24 이하` · `단 6:11-15
 *     참조` 처럼 괄호 없이 적는 것이 이 리포의 관례다(docs/MVP-DANIEL-CONTENT.md §끝).
 *     이 두 표기를 섞으면 규약을 적은 자리마다 빨간불이 켜진다.
 *
 * 상한만 재면 **미션이 6:23 보다 일찍 끝나도 통과** 한다(공집합 전칭명제). 그래서 종료점이
 * 실제로 존재하는지 — 정본 경로와 축약 블록 **양쪽** 이 6:23 으로 닫히는지 — 를 같이 잰다.
 */
class DanielViolenceExclusionTest {

    @Test
    fun `다니엘 미션은 단 6-23 에서 끝난다 — 그 이후 절은 자막·참조 어디에도 없다`() {
        val texts = userFacingScalars()

        assertThat(texts)
            .describedAs("daniel.yml 에서 스칼라를 한 건도 못 읽었다 — 파일 탐색이나 파싱이 깨졌다")
            .isNotEmpty()

        // (a) 기계가 읽는 참조: `dan-6:N`
        val refVerses = texts.flatMap { (path, text) ->
            REF.findAll(text).map { path to it.groupValues[1].toInt() }
        }
        assertThat(refVerses)
            .describedAs("단 6장 참조가 0건이다 — 검사 대상이 비면 아래 상한 판정은 아무것도 재지 않는다")
            .isNotEmpty()

        // (b) 사람이 읽는 인용: `(단 6:N)`
        val citedVerses = texts.flatMap { (path, text) ->
            CITATION.findAll(text).map { path to it.groupValues[1].toInt() }
        }
        assertThat(citedVerses)
            .describedAs("단 6장 괄호 인용이 0건이다 — 자막 표기가 바뀌었으면 CITATION 을 같이 고칠 것")
            .isNotEmpty()

        val beyond = (refVerses + citedVerses).filter { it.second > LAST_VERSE }
            .map { "${it.first} → 단 6:${it.second}" }
            .sorted()

        assertThat(beyond)
            .describedAs(
                "단 6:%d 이후 절이 노출 텍스트에 들어왔다. 이 미션은 6:%d 에서 끝난다 — " +
                    "참소자와 그 가족의 처형은 인물 전 Scene 공통으로 배제된 소재다.",
                LAST_VERSE,
                LAST_VERSE,
            )
            .isEmpty()

        // 종료점이 실재한다 — 정본 경로와 축약 블록 양쪽 모두.
        val closing = texts.filter { CITATION.find(it.second)?.groupValues?.get(1)?.toInt() == LAST_VERSE }
        assertThat(closing.map { it.first })
            .describedAs("6:%d 인용이 없다 — 상한만 지키고 종료점이 사라지면 이 검사는 빈 초록이 된다", LAST_VERSE)
            .isNotEmpty()
        assertThat(closing.any { it.first.contains("conditional_blocks") })
            .describedAs(
                "축약 블록(건너뛴 사람의 경로)에 6:%d 이 없다. 건너뛴 사람도 같은 자리에서 끝나야 한다.",
                LAST_VERSE,
            )
            .isTrue()
    }

    @Test
    fun `배제 사유는 문서에 적혀 있고 시나리오 값에는 적혀 있지 않다`() {
        // 게이트의 structural_check 가 지목하는 나머지 절반이다 — "왜 뺐는지" 는 문서가 갖고,
        // 시나리오 값은 갖지 않는다. 값에 적으면 payload 로 복사돼 화면에 나갈 길이 생긴다.
        val doc = repoRoot().resolve("docs/MVP-DANIEL-CONTENT.md").toFile()
        assertThat(doc)
            .describedAs("배제 사유를 담은 문서가 없다 — 게이트의 structural_check 가 가리키는 자리다")
            .exists()
        assertThat(doc.readText(StandardCharsets.UTF_8))
            .describedAs("문서에 단 6:24 이하 배제 항목이 없다")
            .contains(EXCLUSION_MARKER)

        val leaked = userFacingScalars().filter { it.second.contains(EXCLUSION_MARKER) }.map { it.first }
        assertThat(leaked)
            .describedAs(
                "배제 사유가 시나리오 *값* 에 들어왔다. extras 는 payload 로 통째 복사된다 — " +
                    "무엇을 왜 뺐는지는 문서와 주석에 두고 값에는 두지 않는다.",
            )
            .isEmpty()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** daniel.yml 의 스칼라 (경로, 값). `safety_gates` 서브트리는 뺀다 — 배제 선언이 거기 있다. */
    private fun userFacingScalars(): List<Pair<String, String>> {
        val url = requireNotNull(javaClass.classLoader.getResource("scenarios/daniel.yml")) {
            "클래스패스에 scenarios/daniel.yml 이 없다"
        }
        val file = File(url.toURI())
        @Suppress("UNCHECKED_CAST")
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
        val out = mutableListOf<Pair<String, String>>()
        for ((k, v) in root) {
            if (k == "safety_gates") continue
            collect(v, k.toString(), out)
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

    /**
     * 리포 루트. 작업 디렉터리에 기대지 않는다 — 그건 러너가 어디서 부르느냐에 따라 바뀐다.
     * `ContentSafetyGateEnforcementTest.moduleRoot()` 와 같은 방식으로 클래스 위치에서 거슬러
     * 올라가 `src/main/kotlin` 을 가진 모듈(backend)을 찾고, 그 부모를 루트로 본다.
     */
    private fun repoRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p?.parent) { "리포 루트 탐색 실패" }
    }

    private companion object {
        /** 이 미션의 종료점. 이 절 이후는 인물 전 Scene 공통 배제다. */
        const val LAST_VERSE = 23

        /** 기계가 읽는 참조 표기 — `scripture_ref` · `additional_refs`. */
        val REF = Regex("""\bdan-6:(\d+)""")

        /** 사람이 읽는 인용 표기 — 자막 끝의 `(단 6:23)`. 괄호가 있어야 성구로 센다. */
        val CITATION = Regex("""\(단\s*6:(\d+)\)""")

        /** 문서에는 있어야 하고 시나리오 값에는 없어야 하는 표기. */
        const val EXCLUSION_MARKER = "단 6:24 이하"
    }
}
