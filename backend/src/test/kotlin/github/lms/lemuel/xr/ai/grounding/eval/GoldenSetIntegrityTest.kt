package github.lms.lemuel.xr.ai.grounding.eval

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.application.SentenceSplitter
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RuntimeCrisisRegex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 골든셋 **1단 기계 검증** — 스키마·정합성·본문 출처를 네트워크 없이 확인한다.
 * CONTENT-EVALUATION-GATES.md 의 1단(위반이 스스로 드러나는 결정론적 검사)에 해당하며,
 * `GEMINI_API_KEY` 와 무관하게 **CI 에서 항상 돈다** — 라이브 측정이 skip 되는 동안
 * 데이터셋이 조용히 썩는 것을 막는 유일한 방어선이다.
 */
class GoldenSetIntegrityTest {

    private val loader = ClasspathGoldenSetAdapter(jacksonObjectMapper())
    private val dataset = loader.load(GoldenSet.DEFAULT_VERSION)

    /** 런타임과 *같은* 정규식을 쓴다 — 검사용 사본을 두면 사본만 맞고 런타임은 샐 수 있다. */
    private val crisisScanner = CrisisKeywordScanner(RuntimeCrisisRegex.load())

    @Test
    fun `파일명과 id 가 일치하고 id 는 유일하다`() {
        dataset.fixtures.forEach { fx ->
            // id 는 리포트·로그의 조인 키다. 파일명과 어긋나면 회귀를 파일로 되짚을 수 없다.
            assertThat(fx.sourceName)
                .describedAs("픽스처 id '${fx.id}' 와 파일명이 다르다")
                .isEqualTo("${fx.id}.json")
        }
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
    fun `성경 본문은 원문 대조를 마친 절 전문만 재사용한다`() {
        // 규약(eval/grounding/README.md §5): 새 구절을 쓰려면 개역개정 원문 대조(대한성서공회)가
        // 선행해야 한다. 이 목록을 늘리는 편집 자체가 그 대조를 강제하는 관문이다.
        // 다만 이 검사는 픽스처끼리의 *일관성*만 보장한다 — 맵의 값 자체가 원문인지는
        // 사람이 1차 출처와 대조해야 하고, 그 대조를 건너뛰면 검사는 통과하면서 데이터는 틀린다.
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
    fun `위기 스캐너가 이 검사 안에서 실제로 동작한다`() {
        // 아래 R1 검사는 "0건" 을 근거로 통과한다. 스캐너가 죽어 있어도 0건이므로,
        // 먼저 살아 있음을 보이지 않으면 그 통과는 아무 뜻이 없다.
        // 2026-08-04 사고가 정확히 이 모양이었다 — 검사는 초록이었고, 틀린 값끼리 비교하고 있었다.
        // (`safety.crisis-keywords-regex` 는 placeholder 라 벗기지 않고 컴파일하면 조용히 0건이 된다.)
        listOf(
            "죽고 싶다는 생각이 듭니다" to "critical",
            "자해하고 싶어요" to "critical",
            "같이 묻히고 싶다" to "high",
            "사라지고 싶습니다" to "high",
            "유서를 써 뒀어요" to "medium",
        ).forEach { (text, severity) ->
            assertThat(crisisScanner.scan(text).severity)
                .describedAs("양성 대조 실패 — 스캐너가 '%s' 를 못 잡는다. R1 0건은 증거가 아니다", text)
                .isEqualTo(severity)
        }
        // 음성 대조가 없으면 "무엇이든 잡는" 스캐너로도 위 다섯이 통과한다.
        assertThat(crisisScanner.scan("오늘은 시편 88편을 읽었습니다").matched)
            .describedAs("음성 대조 실패 — 스캐너가 평범한 문장까지 잡는다")
            .isFalse()
    }

    @Test
    fun `골든셋 본문에 R1 위기 키워드가 없다`() {
        // README §6 의 "R1(자살사고) 본문을 이번 확장에 한 건도 넣지 않았다" 는 지금까지
        // 문장으로만 있었다. 지키는 검사가 없으면 다음 픽스처를 넣는 사람은 그 규칙을
        // 읽지 않고, 읽어도 자기 눈으로 판정하게 된다.
        //
        // 규칙의 근거는 §4 다 — R1 을 다루는 픽스처는 어떤 자동 합의로도 승격할 수 없고
        // 인간 안전 검토자 사인오프가 마지막 게이트다. 후보로 쌓아 두는 것 자체가 부담이 된다.
        // 그리고 이 본문들은 근거성 게이트 평가에 그대로 입력되므로, R1 문장이 섞이면
        // 위기 신호가 *안전 파이프라인이 아니라 채점 파이프라인* 으로 흘러간다.
        val hits = dataset.fixtures.flatMap { fx ->
            // 검토 rationale 은 일부러 뺀다. rationale 은 "이 본문이 왜 위험한가" 를 적는
            // 검토자용 메타이고, 위험을 이름으로 부르지 못하면 근거를 쓸 수 없다.
            // 실제로 gnostic-body-prison·suffering-faith-deficiency 의 rationale 이
            // '자살'·'자해' 를 그 뜻으로 쓰고 있다. 검사 대상은 사용자에게 노출되는 본문이다.
            val fields = listOf("meditationText" to fx.meditationText) +
                fx.passages.map { "passage(${it.reference})" to it.text }
            fields.mapNotNull { (field, text) ->
                val r = crisisScanner.scan(text)
                if (r.matched) "${fx.id}.$field → ${r.matchedPattern}(${r.severity})" else null
            }
        }

        assertThat(hits)
            .describedAs(
                "골든셋 본문이 위기 키워드에 걸렸다. R1 표본은 사람 안전 검토자의 사인오프 " +
                    "없이는 이 데이터셋에 들어올 수 없다(eval/grounding/README.md §4·§6).",
            )
            .isEmpty()
    }

    @Test
    fun `없는 버전을 읽으면 빈 결과가 아니라 실패한다`() {
        // 조용히 빈 데이터셋을 돌려주면 모든 지표가 null 이 되어, 배선이 깨진 것을
        // "표본이 없다" 로 오독하게 된다.
        assertThatThrownBy { loader.load("v-does-not-exist") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("manifest")
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
        /**
         * 대한성서공회 개역개정 **절 전문**과 글자 그대로 일치하는 본문.
         * (https://www.bskorea.or.kr/bible/korbibReadpage.php — version=GAE)
         *
         * 전문을 그대로 두는 것이 규칙이다. 절의 일부만 따오면 "어디까지가 맞는 인용인가" 에
         * 정답이 없어져, 누군가 표현을 다듬어도 아무도 눈치채지 못한다 — 실제로 2026-08-04 에
         * 욥 1:21·시 88:18 두 건이 그렇게 개역한글과 뒤섞인 문장으로 남아 있었고,
         * 이 맵이 그 틀린 문장을 "검증본" 으로 담고 있어 검사가 틀린 값끼리 대조하고 있었다.
         */
        val VERIFIED_PASSAGES = mapOf(
            "욥 42:5" to "내가 주께 대하여 귀로 듣기만 하였사오나 이제는 눈으로 주를 뵈옵나이다",
            "욥 1:21" to "이르되 내가 모태에서 알몸으로 나왔사온즉 또한 알몸이 그리로 돌아가올지라 " +
                "주신 이도 여호와시요 거두신 이도 여호와시오니 여호와의 이름이 찬송을 받으실지니이다 하고",
            "시 88:1" to "여호와 내 구원의 하나님이여 내가 주야로 주 앞에서 부르짖었사오니",
            "시 88:18" to "주는 내게서 사랑하는 자와 친구를 멀리 떠나게 하시며 내가 아는 자를 흑암에 두셨나이다",
            "요 9:3" to "예수께서 대답하시되 이 사람이나 그 부모의 죄로 인한 것이 아니라 그에게서 하나님이 하시는 일을 나타내고자 하심이라",
        )
    }
}
