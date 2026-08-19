package github.lms.lemuel.xr.game.domain

import github.lms.lemuel.xr.LemuelXrApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 런타임 노출 게이트 — 룻이 **결정 기록과 AR 폐쇄 없이** 열리지 않는지 본다.
 *
 * ───────────────────────── 왜 이 테스트가 필요했나 ─────────────────────────
 *
 * `ScenarioYamlLoader.loadAll()` 은 `Character.entries` 를 순회한다. 디렉터리를 훑지 않는다.
 * 그래서 `scenarios/ruth.yml` 은 완성된 콘텐츠로 존재하면서도 로드되지 않을 수 있었고,
 * enum 에 `RUTH("ruth")` 한 줄을 넣는 것이 곧 노출이었다.
 *
 * 그 한 줄이 게이트라는 사실은 2026-08-12 까지 **문장으로만** 있었다. 더 나쁜 건 기존
 * 스위트의 검사 *방향* 이었다. `모든 Character 에 시나리오 yml 이 존재한다` 는 enum 에
 * 있는데 파일이 없는 경우를 잡는다. RUTH 를 추가하면 파일이 이미 있으므로 그 테스트는
 * **더 만족스럽게 통과한다.** 있어야 할 것의 부재는 봤고, 있으면 안 될 것의 존재는
 * 아무도 안 봤다. 그 방향을 여기서 채운다.
 *
 * ──────────────────── 2026-08-20 — 요구를 바꿨다 ────────────────────
 *
 * 처음 이 테스트는 **사람 두 명의 사인오프**(신학 · 정신건강·안전)를 요구했다.
 * 그 요구는 철회했다. 이미 열려 있는 일곱 인물 전부에 R1(자해 발화) 리스너가 있는데
 * 그중 어느 것도 대장을 거치지 않았고, 룻에만 대장이 있던 건 밀도가 높아서
 * 그때 마침 만들었기 때문이다. 그 상태에서 게이트를 유지하는 길은 둘뿐이었다 —
 * 나머지 일곱을 계속 덜 걸린 채로 두거나, **아무도 검토하지 않은 항목에 이름을 적어
 * 통과시키거나.** 후자는 이 리포가 계속 잡아 온 빈 통제 그 자체다.
 *
 * 그래서 재는 대상을 바꿨다. 이제 이 테스트가 요구하는 것은 두 가지다.
 *
 * 1. **누가 알고서 열기로 했는가** — 대장의 «노출 결정» 한 줄(이름 + 날짜).
 *    이건 안전의 증명이 아니라 책임의 기록이다.
 * 2. **그 결정에 딸린 조치가 실제로 되어 있는가** — 룻의 AR 폐쇄.
 *    이쪽이 이 테스트의 이빨이다. 문장이 아니라 설정과 파일을 본다.
 *
 * ───────────────────────── 이 테스트가 재지 않는 것 ─────────────────────────
 *
 * 콘텐츠가 안전한가. 전문 검토를 받았는가. 둘 다 안 본다 — 받지 않았다는 사실이
 * 대장에 적혀 있다. 여기서 재는 것은 "결정 기록과 AR 폐쇄 없이 노출이 열리지
 * 않았는가" 하나뿐이다. 이 테스트를 지우는 것도 물리적으로는 가능하지만,
 * 그 삭제는 diff 에 남는다.
 */
class RuntimeExposureSignoffTest {

    /**
     * 결정 한 줄의 형식. 체크박스·결정자·날짜 셋을 한 번에 판정한다.
     * 이름이 비어 있거나 날짜가 YYYY-MM-DD 가 아니면 매치하지 않는다 —
     * `[x]` 만 찍고 이름을 안 적는 것이 가장 흔한 통과 방식이라 형식으로 막는다.
     */
    private val decisionLine = Regex(
        """^- \[x] 노출 결정 — 결정자:\s*(?<who>\S[^/\n]*?)\s*/\s*날짜:\s*(?<when>\d{4}-\d{2}-\d{2})\s*$""",
        RegexOption.MULTILINE,
    )

    @Test
    fun `룻 런타임 노출은 결정 기록 없이 열리지 않는다`() {
        val ledgerPath = repoRoot().resolve("docs/RUTH-RUNTIME-SIGNOFF.md")
        assertThat(Files.exists(ledgerPath))
            .describedAs("노출 대장이 없다: %s — 게이트를 지우려면 테스트도 같이 지워라", ledgerPath)
            .isTrue()

        val ledger = Files.readString(ledgerPath, StandardCharsets.UTF_8)

        // 줄 자체가 (체크 여부와 무관하게) 살아 있는지 먼저 본다.
        // 줄을 지우면 "체크된 줄이 없다" 가 아니라 "검사할 게 없다" 가 되기 때문이다.
        assertThat(ledger)
            .describedAs("노출 대장에서 «노출 결정» 줄이 사라졌다")
            .contains("] 노출 결정 — 결정자:")

        // 전문 검토를 받지 않았다는 사실이 대장에 남아 있어야 한다.
        // 이 문장이 빠지면 대장은 "검토를 거쳤다" 로 조용히 읽히기 시작한다.
        assertThat(ledger)
            .describedAs("대장에서 '전문 검토 없음' 기록이 사라졌다 — 없는 보증을 있는 것처럼 만들지 마라")
            .contains("전문 신학 검토 없음")
            .contains("전문 정신건강 검토 없음")

        if (!isExposed()) return

        assertThat(decisionLine.find(ledger))
            .describedAs(
                "Character enum 에 RUTH 가 들어왔는데 «노출 결정» 이 비어 있다. " +
                    "enum 한 줄이 곧 사용자 노출이다 — docs/RUTH-RUNTIME-SIGNOFF.md 의 " +
                    "결정 줄(결정자 이름 + YYYY-MM-DD)을 채운 뒤에 열어라",
            )
            .isNotNull()
    }

    /**
     * 결정에 딸린 조치 — 룻의 AR 은 닫혀 있어야 한다.
     *
     * 패스스루는 씬을 사용자의 실제 방에 놓는다. 룻 Scene 4(밤·권력 비대칭)를 그렇게
     * 놓는 판단을 한 사람이 없어서, 그 축을 열지 않는 것을 노출의 **조건** 으로 삼았다.
     * 설정과 시드 **둘 다** 보는 이유: 목록만 고쳐 두면 환경변수 한 줄로 되살아나고,
     * 그때 에셋이 이미 있으면 아무 저항 없이 열린다.
     */
    @Test
    fun `룻이 열려 있다면 AR 은 설정에서도 시드에서도 닫혀 있다`() {
        if (!isExposed()) return

        val yml = Files.readString(
            repoRoot().resolve("backend/src/main/resources/application.yml"),
            StandardCharsets.UTF_8,
        )
        val default = Regex("""ar-enabled-missions:\s*\$\{XR_AR_MISSIONS:([^}]*)}""").find(yml)
        assertThat(default).describedAs("application.yml 에서 ar-enabled-missions 기본값을 못 찾았다").isNotNull()

        val missions = default!!.groupValues[1].split(",").map { it.trim() }
        assertThat(missions)
            .describedAs("룻이 AR 미션 목록에 들어왔다 — 노출 조건은 VR 전용이다 (docs/RUTH-RUNTIME-SIGNOFF.md)")
            .doesNotContain("ruth")

        val arSeeds = repoRoot().resolve("backend/src/main/resources/manifests/ruth")
            .let { root -> if (Files.isDirectory(root)) Files.walk(root).use { s -> s.filter { it.fileName.toString() == "ar" }.toList() } else emptyList() }
        assertThat(arSeeds)
            .describedAs("룻 AR 시드가 돌아왔다 — 목록만 닫아두면 환경변수 한 줄로 열린다")
            .isEmpty()
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

        // subset 이지 정확히 일치가 아니다 — 룻이 열리면 이 목록은 비게 되고, 그건 정상이다.
        // 여기서 막을 것은 *새로운* 미배선 시나리오다.
        assertThat(onDisk.filterNot { it in exposed })
            .describedAs(
                "enum 에 없는 시나리오 yml 이 늘었다. 파일만 두면 로드되지 않아 안전해 보이지만, " +
                    "그 상태를 지키는 게이트는 룻 것뿐이다 — 새 인물도 노출 대장을 만들어라 " +
                    "(docs/RUTH-RUNTIME-SIGNOFF.md 형식)",
            )
            .isSubsetOf("ruth")
    }

    private fun isExposed(): Boolean = Character.entries.any { it.dbValue == "ruth" }

    private fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    /** LemuelXrApplication code-source 위치에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    private fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }
}
