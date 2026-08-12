package github.lms.lemuel.xr.game.domain

import github.lms.lemuel.xr.LemuelXrApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 런타임 노출 게이트 — 사인오프 없이 `Character` enum 이 열리지 않는지 본다.
 *
 * ───────────────────────── 왜 이 테스트가 필요했나 ─────────────────────────
 *
 * `ScenarioYamlLoader.loadAll()` 은 `Character.entries` 를 순회한다. 디렉터리를 훑지 않는다.
 * 그래서 `scenarios/ruth.yml` 은 47KB 의 완성된 콘텐츠로 존재하지만 **로드되지 않고**,
 * 사용자 노출은 0이다. enum 에 `RUTH("ruth")` 한 줄을 넣는 것이 곧 노출이다.
 *
 * 그 한 줄이 사인오프 게이트라는 사실은 2026-08-12 까지 **문장으로만** 있었다 —
 * `docs/MVP-RUTH-CONTENT.md` 머리말과 `scenarios/ruth.yml` 주석. 문장은 실행되지 않는다.
 *
 * 더 나쁜 건 기존 스위트의 검사 *방향* 이었다. `모든 Character 에 시나리오 yml 이
 * 존재하고 로드된다` 는 enum 에 있는데 파일이 없는 경우를 잡는다. RUTH 를 추가하면
 * 파일이 이미 있으므로 그 테스트는 **더 만족스럽게 통과한다.** 있어야 할 것의 부재는
 * 봤고, 있으면 안 될 것의 존재는 아무도 안 봤다. 그 방향을 여기서 채운다.
 *
 * R1(자해 발화) 콘텐츠는 어떤 자동 합의로도 최종 승인할 수 없다
 * (`docs/CONTENT-EVALUATION-GATES.md` §1 3단). 룻은 5개 Scene 전부에 R1 리스너가 있다.
 * 그래서 이 게이트가 요구하는 것은 리뷰 승인이 아니라 **사람 두 명의 이름과 날짜** 다.
 *
 * ───────────────────────── 이 테스트가 재지 않는 것 ─────────────────────────
 *
 * 체크박스는 사람이 봤다는 *기록* 이지 콘텐츠가 안전하다는 증명이 아니다.
 * 여기서 재는 것은 "두 사인오프 없이 노출이 열리지 않았는가" 하나뿐이다.
 * 게이트를 열려면 `docs/RUTH-RUNTIME-SIGNOFF.md` 의 두 줄을 채워라 —
 * 이 테스트를 지우는 것도 물리적으로는 가능하지만, 그 삭제는 diff 에 남는다.
 */
class RuntimeExposureSignoffTest {

    /**
     * 사인오프 한 줄의 형식. 체크박스·검토자·날짜 셋을 한 번에 판정한다.
     * 이름이 비어 있거나 날짜가 YYYY-MM-DD 가 아니면 매치하지 않는다 —
     * `[x]` 만 찍고 이름을 안 적는 것이 가장 흔한 통과 방식이라 형식으로 막는다.
     */
    private fun signed(ledger: String, label: String): Boolean =
        Regex(
            """^- \[x] $label 사인오프 — 검토자:\s*(?<who>\S[^/\n]*?)\s*/\s*날짜:\s*(?<when>\d{4}-\d{2}-\d{2})\s*$""",
            RegexOption.MULTILINE,
        ).find(ledger) != null

    @Test
    fun `룻 런타임 노출은 인간 사인오프 2인 없이 열리지 않는다`() {
        val ledgerPath = repoRoot().resolve("docs/RUTH-RUNTIME-SIGNOFF.md")
        assertThat(Files.exists(ledgerPath))
            .describedAs("사인오프 대장이 없다: %s — 게이트를 지우려면 테스트도 같이 지워라", ledgerPath)
            .isTrue()

        val ledger = Files.readString(ledgerPath, StandardCharsets.UTF_8)

        // 대장에 두 줄이 (체크 여부와 무관하게) 살아 있는지 먼저 본다.
        // 줄을 지우면 "체크된 줄이 없다" 가 아니라 "검사할 게 없다" 가 되기 때문이다.
        for (label in listOf("신학 검토", "정신건강·안전 검토")) {
            assertThat(ledger)
                .describedAs("사인오프 대장에서 '%s 사인오프' 줄이 사라졌다", label)
                .contains("] $label 사인오프 — 검토자:")
        }

        val theology = signed(ledger, "신학 검토")
        val safety = signed(ledger, "정신건강·안전 검토")
        val exposed = Character.entries.any { it.dbValue == "ruth" }

        if (exposed) {
            assertThat(theology && safety)
                .describedAs(
                    "Character enum 에 RUTH 가 들어왔는데 사인오프가 없다 " +
                        "(신학=%s · 정신건강·안전=%s). enum 한 줄이 곧 사용자 노출이다. " +
                        "docs/RUTH-RUNTIME-SIGNOFF.md 의 두 줄을 채운 뒤에 열어라",
                    theology, safety,
                )
                .isTrue()
        } else {
            // 현재 상태를 못 박는다. 이 단언이 빨개졌다면 그건 게이트가 열렸다는 뜻이고,
            // 위 분기로 넘어가 사인오프를 요구받는다. 여기서 실패할 일은 없다 —
            // 이 줄은 "닫혀 있음"을 기록으로 남기기 위한 것이다.
            assertThat(Files.exists(repoRoot().resolve("backend/src/main/resources/scenarios/ruth.yml")))
                .describedAs("ruth.yml 은 존재하되 로드되지 않는 상태여야 한다")
                .isTrue()
        }
    }

    /** enum 에 없는 시나리오 yml 이 또 생기면, 그것도 같은 게이트를 거쳐야 한다는 사실을 드러낸다. */
    @Test
    fun `enum 에 없는 시나리오는 룻 하나뿐이다`() {
        val dir = repoRoot().resolve("backend/src/main/resources/scenarios")
        val onDisk = Files.list(dir).use { s ->
            s.map { it.fileName.toString() }
                .filter { it.endsWith(".yml") }
                .map { it.removeSuffix(".yml") }
                .sorted()
                .toList()
        }
        val exposed = Character.entries.map { it.dbValue }.toSet()

        // subset 이지 정확히 일치가 아니다 — 룻이 사인오프를 받고 enum 에 들어가면
        // 이 목록은 비게 되고, 그건 게이트가 열린 정상 상태다. 여기서 막을 것은
        // *새로운* 미배선 시나리오다.
        assertThat(onDisk.filterNot { it in exposed })
            .describedAs(
                "enum 에 없는 시나리오 yml 이 늘었다. 파일만 두면 로드되지 않아 안전해 보이지만, " +
                    "그 상태를 지키는 게이트는 룻 것뿐이다 — 새 인물도 사인오프 대장을 만들어라 " +
                    "(docs/RUTH-RUNTIME-SIGNOFF.md 형식)",
            )
            .isSubsetOf("ruth")
    }

    private fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    /** LemuelXrApplication code-source 위치에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    private fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }
}
