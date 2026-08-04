package github.lms.lemuel.xr.ai.grounding.eval

import github.lms.lemuel.xr.ai.grounding.application.SentenceSplitter
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * 골든셋 **1단 기계 검증** — 스키마·정합성·본문 출처를 네트워크 없이 확인한다.
 * CONTENT-EVALUATION-GATES.md 의 1단(위반이 스스로 드러나는 결정론적 검사)에 해당하며,
 * `GEMINI_API_KEY` 와 무관하게 **CI 에서 항상 돈다** — 라이브 측정이 skip 되는 동안
 * 데이터셋이 조용히 썩는 것을 막는 유일한 방어선이다.
 */
class GroundingDatasetTest {

    private val dataset = GroundingDataset.load()

    @Test
    fun `파일명과 id 가 일치하고 id 는 유일하다`() {
        val files = Files.list(GroundingDataset.datasetDir().resolve("fixtures")).use { s ->
            s.map { it.fileName.toString() }.filter { it.endsWith(".json") }.sorted().toList()
        }
        val expectedNames = dataset.fixtures.map { "${it.id}.json" }.sorted()
        // id 는 리포트·ELK 로그의 조인 키다. 파일명과 어긋나면 회귀를 파일로 되짚을 수 없다.
        assertThat(expectedNames).isEqualTo(files)
        assertThat(dataset.fixtures.map { it.id }).doesNotHaveDuplicates()
    }

    @Test
    fun `클래스는 manifest 에 정의돼 있고 기대 상태가 일치한다`() {
        dataset.fixtures.forEach { fx ->
            val declared = dataset.expectedStatusOf(fx.`class`)
            assertThat(declared)
                .describedAs("픽스처 ${fx.id}: manifest 에 없는 클래스 '${fx.`class`}'")
                .isNotNull()
            // 클래스가 기대 상태를 정의하므로 픽스처가 그걸 뒤집으면 집계가 거짓말을 한다.
            assertThat(fx.expectedStatus)
                .describedAs("픽스처 ${fx.id}: 클래스 '${fx.`class`}' 의 기대 상태는 $declared")
                .isEqualTo(declared)
            assertThat(GroundingStatus.entries.map { it.name }).contains(fx.expectedStatus)
        }
    }

    @Test
    fun `검토 메타가 채워져 있고 draft 라벨은 signed_off 로 새지 않는다`() {
        dataset.fixtures.forEach { fx ->
            assertThat(fx.review.rationale)
                .describedAs("픽스처 ${fx.id}: rationale 이 비었다 — 라벨을 의심할 때 읽을 근거가 없다")
                .isNotBlank()
            assertThat(fx.review.labeledAt).describedAs("픽스처 ${fx.id}: labeledAt 없음").isNotBlank()
            assertThat(fx.reviewStatus).isNotNull()
        }
        // 규약의 핵심: LLM 이 만든 라벨 후보가 사람 사인오프 없이 게이트 표본으로 승격되면
        // 게이트가 자기 자신을 채점하게 된다.
        val selfLabeled = dataset.signedOff.filter { it.review.labeledBy == "claude-draft" }
        assertThat(selfLabeled.map { it.id })
            .describedAs("claude-draft 라벨이 signed_off 로 승격됐다 — 사람 사인오프가 필요하다")
            .isEmpty()
        // signed_off 는 사람이거나, 코드 분기가 라벨을 확정하는 구조적 케이스여야 한다.
        assertThat(dataset.signedOff.map { it.review.labeledBy }.distinct())
            .isSubsetOf(listOf("human", "structural"))
    }

    @Test
    fun `성경 본문은 원문 대조를 마친 구절만 재사용한다`() {
        // 규약(eval/grounding/README.md §5): 새 구절을 쓰려면 개역개정 원문 대조(대한성서공회)가
        // 선행해야 한다. 이 목록을 늘리는 편집 자체가 그 대조를 강제하는 관문이다.
        dataset.fixtures.forEach { fx ->
            fx.passages.forEach { p ->
                val verified = VERIFIED_PASSAGES[p.reference]
                assertThat(verified)
                    .describedAs("픽스처 ${fx.id}: 미검증 구절 '${p.reference}' — 원문 대조 후 VERIFIED_PASSAGES 에 추가할 것")
                    .isNotNull()
                assertThat(p.text)
                    .describedAs("픽스처 ${fx.id}: '${p.reference}' 본문이 검증본과 다르다")
                    .isEqualTo(verified)
            }
        }
    }

    @Test
    fun `구조적 클래스는 임베딩 이전 분기에 실제로 도달한다`() {
        // 이 두 클래스는 라벨이 코드 분기로 확정되기에 사람 검토를 면제받는다.
        // 면제의 전제가 실제로 성립하는지를 여기서 확인한다 — 안 그러면 근거 없는 signed_off 가 된다.
        dataset.fixtures.filter { it.`class` == "structural-no-evidence" }.forEach {
            assertThat(it.passages).describedAs("${it.id}: 근거 본문이 있으면 NO_EVIDENCE 분기에 안 걸린다").isEmpty()
            assertThat(SentenceSplitter.split(it.meditationText))
                .describedAs("${it.id}: 문장이 비면 INCONCLUSIVE 가 먼저 걸려 NO_EVIDENCE 에 도달하지 못한다")
                .isNotEmpty()
        }
        dataset.fixtures.filter { it.`class` == "structural-inconclusive" }.forEach {
            assertThat(SentenceSplitter.split(it.meditationText))
                .describedAs("${it.id}: 문장이 분리되면 INCONCLUSIVE 분기에 안 걸린다")
                .isEmpty()
        }
    }

    @Test
    fun `표본 수 현황을 남긴다`() {
        val targets = dataset.manifest.targets
        val perClass = dataset.signedOff.groupingBy { it.`class` }.eachCount()
        val report = buildString {
            append("\n=== golden set 현황 (${dataset.manifest.dataset} ${dataset.manifest.version}) ===\n")
            append("signed_off=${dataset.signedOff.size} / 목표 ${targets.minSignedOff}   draft=${dataset.drafts.size}\n")
            dataset.manifest.classes.forEach { c ->
                val n = perClass[c.id] ?: 0
                val flag = if (n < targets.minPerClassSignedOff) "  ← 부족" else ""
                append("  %-26s signed_off=%-3d (목표 %d)%s\n".format(c.id, n, targets.minPerClassSignedOff, flag))
            }
        }
        println(report)
        // 표본 부족은 사실이고 알려진 상태다 — 여기서 빌드를 깨면 데이터셋을 채우기 전까지
        // 아무 작업도 못 한다. 실패시키지 않고 매 실행 로그에 남겨 압박만 유지한다.
        assertThat(dataset.signedOff).describedAs("signed_off 표본이 0 — 로딩이 깨졌다").isNotEmpty()
    }

    private companion object {
        /** 2026-07-19 사람이 개역개정 원문 대조를 마친 구절. 추가하려면 같은 대조를 거칠 것. */
        val VERIFIED_PASSAGES = mapOf(
            "욥 42:5" to "내가 주께 대하여 귀로 듣기만 하였사오나 이제는 눈으로 주를 뵈옵나이다",
            "욥 1:21" to "여호와께서 주셨고 여호와께서 거두셨으니 여호와의 이름이 찬송을 받으실지니이다",
            "시 88:1" to "여호와 내 구원의 하나님이여 내가 주야로 주 앞에서 부르짖었사오니",
            "시 88:18" to "주는 나의 사랑하는 자와 친구를 내게서 멀리 떠나게 하시며 내가 아는 자를 흑암에 두셨나이다",
            "요 9:3" to "예수께서 대답하시되 이 사람이나 그 부모의 죄로 인한 것이 아니라 그에게서 하나님이 하시는 일을 나타내고자 하심이라",
        )
    }
}
