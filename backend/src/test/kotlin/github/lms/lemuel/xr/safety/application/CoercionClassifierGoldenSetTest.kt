package github.lms.lemuel.xr.safety.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * **L3a 를 골든셋에서 잰다.**
 *
 * 임베딩이 필요 없다 — [CoercionStructureClassifier] 는 결정론적이므로 `GEMINI_API_KEY` 없이
 * CI 에서 그대로 돈다. 근거성 게이트의 라이브 측정이 CI 에서 불가능한 것과 대비되는 지점이고,
 * L3 게이트를 결정론적으로 만든 이유 중 하나다.
 *
 * ## 여기 나오는 수치는 인샘플이다
 * 규칙 어휘군을 만들 때 저자가 `suffering-justification` 15건과 `orthodox` 12건의 원문을
 * **모두 읽었다.** 그러므로 아래 recall·오탐률은 "이 분류기가 잘 작동한다"의 증거가 아니라
 * *"규칙이 표본을 덮는다"* 이상을 말하지 못한다. 이 리포에는 그 착각의 전례가 있다 —
 * 금지 토큰 lint 의 인샘플 7/7 대 아웃오브샘플 0/27.
 *
 * 실질을 보려면 세 곳을 같이 봐야 한다:
 * - [CoercionClassifierMetamorphicTest] — 인칭 치환 불변성(설계가 보장하는 성질)
 * - [CoercionClassifierKnownGapTest] — 유의어 재작성에 뚫리는 실물
 * - 아래 홀드아웃 표 — 단, 저자가 홀드아웃 원문도 읽었으므로 **낙관 상한**이다
 */
class CoercionClassifierGoldenSetTest {

    private val classifier = CoercionStructureClassifier()
    private val dataset: GoldenSet.Loaded = ClasspathGoldenSetAdapter(jacksonObjectMapper())
        .load(GoldenSet.DEFAULT_VERSION)

    @Test
    fun `강제 구조 표본을 잡고 정통 묵상은 건드리지 않는다`() {
        val coercive = signedOff(COERCIVE_CLASS)
        val orthodox = signedOff(ORTHODOX_CLASS)

        val caught = coercive.filter { classifier.classify(it.meditationText).coercive }
        val falsePositives = orthodox.filter { classifier.classify(it.meditationText).coercive }

        println(report(coercive, orthodox))

        assertThat(coercive)
            .describedAs("$COERCIVE_CLASS 표본이 없다 — 골든셋 로딩이 깨졌다")
            .isNotEmpty()
        assertThat(falsePositives.map { it.id })
            .describedAs(
                "정통 묵상 오탐. L3 는 명제가 아니라 구조를 보므로 정통 표본에는 절대 걸리면 안 된다 — " +
                    "여기가 빨간불이면 규칙이 어휘를 보기 시작했다는 뜻이다.",
            )
            .isEmpty()
        assertThat(caught)
            .describedAs("강제 구조 표본을 하나도 못 잡았다 — 규칙이 통째로 죽었다")
            .isNotEmpty()

        // 미탐 목록은 **크기가 아니라 모양**을 고정한다. 늘어나면 회귀, 줄어들면 목록을 갱신하고
        // 무엇을 새로 잡게 됐는지 커밋 메시지에 남긴다.
        assertThat(coercive.map { it.id } - caught.map { it.id }.toSet())
            .describedAs("L3a 가 못 잡는 강제 구조 표본")
            .isEqualTo(MISSED_BY_L3A)
    }

    /**
     * **절제(ablation) — "네" 한 글자짜리 분류기와 비교한다.**
     *
     * L3a 가 인칭을 안 보기로 한 결정이 값을 치를 만한 것이었는지는, 인칭만 보는 분류기가
     * 이 표본에서 *얼마나 잘해 보이는지* 를 실제로 재 봐야 안다. 기억으로 적지 않고 여기서 잰다.
     */
    @Test
    fun `2인칭 표지 단독 기준선을 함께 잰다 — 골든셋만으로는 둘을 구별할 수 없다`() {
        val coercive = signedOff(COERCIVE_CLASS)
        val orthodox = signedOff(ORTHODOX_CLASS)
        val secondPerson = Regex("네가|네 [가-힣]|너를|너는|너에게|네게|당신")

        fun rate(hit: Int, n: Int) = "%.3f (%d/%d)".format(hit.toDouble() / n, hit, n)
        fun baselineRecall(transform: (String) -> String) =
            coercive.count { secondPerson.containsMatchIn(transform(it.meditationText)) }
        fun l3aRecall(transform: (String) -> String) =
            coercive.count { classifier.classify(transform(it.meditationText)).coercive }

        val toFirstPerson: (String) -> String = { t ->
            t.replace("네가", "내가").replace("너를", "나를").replace("너는", "나는")
                .replace("너에게", "나에게").replace("네게", "내게")
                .replace(Regex("네 (?=[가-힣])"), "내 ")
        }

        println(
            buildString {
                append("\n=== 절제 실험 — 인칭 기준선 대 L3a ===\n")
                append("  %-28s %-22s %s\n".format("분류기", "원문 recall", "1인칭 치환 후 recall"))
                append(
                    "  %-28s %-22s %s\n".format(
                        "2인칭 표지 단독",
                        rate(baselineRecall { it }, coercive.size),
                        rate(baselineRecall(toFirstPerson), coercive.size),
                    ),
                )
                append(
                    "  %-28s %-22s %s\n".format(
                        "L3a 관계 구조",
                        rate(l3aRecall { it }, coercive.size),
                        rate(l3aRecall(toFirstPerson), coercive.size),
                    ),
                )
                append("  2인칭 표지 단독 오탐률(정통): ${rate(orthodox.count { secondPerson.containsMatchIn(it.meditationText) }, orthodox.size)}\n")
            },
        )

        // 기준선이 원문에서 *잘해 보이는지* 확인한다. 잘해 보여야 이 절제 실험이 의미가 있다 —
        // 골든셋 수치만으로는 구조 분류기와 대명사 탐지기를 구별할 수 없다는 것이 요점이다.
        assertThat(baselineRecall { it })
            .describedAs("2인칭 표지 단독 기준선이 원문에서 대부분을 잡는다는 사실 자체가 이 표본의 편향이다")
            .isGreaterThanOrEqualTo(coercive.size * 3 / 4)
        assertThat(orthodox.count { secondPerson.containsMatchIn(it.meditationText) })
            .describedAs("정통 표본에 2인칭이 하나도 없다 — 표본 저자의 작성 습관이지 신학이 아니다")
            .isZero()
        // 그리고 치환 뒤에 무너지는 쪽이 기준선이어야 한다.
        assertThat(baselineRecall(toFirstPerson))
            .describedAs("인칭 기준선은 1인칭 치환에 무너져야 한다 — 안 무너지면 치환 함수가 안 듣는 것이다")
            .isLessThan(baselineRecall { it })
        assertThat(l3aRecall(toFirstPerson))
            .describedAs("L3a 는 인칭을 입력으로 쓰지 않으므로 치환에 불변이어야 한다")
            .isEqualTo(l3aRecall { it })
    }

    @Test
    fun `걸린 관계마다 근거 문장과 불투명 규칙 id 가 남는다`() {
        val fixture = signedOff(COERCIVE_CLASS).first { classifier.classify(it.meditationText).coercive }
        val verdict = classifier.classify(fixture.meditationText)

        assertThat(verdict.relations).isNotEmpty()
        verdict.relations.forEach { hit ->
            assertThat(hit.evidence)
                .describedAs("근거 문장이 비었다 — 임상 검토가 무엇을 보고 판단하나")
                .isNotBlank()
            assertThat(hit.ruleId)
                .describedAs("규칙 id 는 자구를 복원할 수 없는 6바이트 해시여야 한다")
                .matches("[0-9a-f]{12}")
            assertThat(hit.ruleId)
                .describedAs("규칙 id 에 자구가 새면 안 된다")
                .doesNotContain(hit.evidence.take(4))
        }
    }

    @Test
    fun `관계 id 는 관계마다 다르고 안정적이다`() {
        val ids = CoercionStructureClassifier.Relation.entries
            .associateWith { CoercionStructureClassifier.ruleIdOf(it) }
        assertThat(ids.values.toSet())
            .describedAs("두 관계가 같은 id 를 쓰면 메트릭에서 구분이 사라진다")
            .hasSameSizeAs(ids.keys)
        // 해시가 바뀌면 대시보드의 시계열이 끊긴다. 값을 여기 박아 그 변경이 의도적이게 만든다.
        assertThat(CoercionStructureClassifier.ruleIdOf(CoercionStructureClassifier.Relation.BLAME))
            .isEqualTo(BLAME_RULE_ID)
    }

    private fun signedOff(className: String) =
        dataset.signedOff.filter { it.`class` == className }.sortedBy { it.id }

    /** [GroundingThresholdSweepReport] 와 **같은** 결정적 분할이어야 두 층의 수치를 비교할 수 있다. */
    private fun stratifiedHalves(
        fixtures: List<GoldenSet.Fixture>,
    ): Pair<List<GoldenSet.Fixture>, List<GoldenSet.Fixture>> {
        val fit = mutableListOf<GoldenSet.Fixture>()
        val holdout = mutableListOf<GoldenSet.Fixture>()
        fixtures.groupBy { it.`class` }.toSortedMap().forEach { (_, group) ->
            group.sortedBy { it.id }.forEachIndexed { i, fx -> (if (i % 2 == 0) fit else holdout).add(fx) }
        }
        return fit to holdout
    }

    private fun report(coercive: List<GoldenSet.Fixture>, orthodox: List<GoldenSet.Fixture>): String {
        val (fit, holdout) = stratifiedHalves(dataset.signedOff)
        val fitIds = fit.map { it.id }.toSet()

        fun half(fixtures: List<GoldenSet.Fixture>, inFit: Boolean) =
            fixtures.filter { (it.id in fitIds) == inFit }

        fun rate(hit: Int, n: Int) = if (n == 0) "n/a" else "%.3f (%d/%d)".format(hit.toDouble() / n, hit, n)

        return buildString {
            append("\n=== L3a 강제 구조 분류기 — 골든셋 측정 ===\n")
            append("⚠️ 규칙 저자가 아래 표본 원문을 모두 읽었다. 인샘플·홀드아웃 모두 낙관 상한이다.\n\n")
            append("-- 전체 --\n")
            append("  recall(강제 구조)      : ${rate(coercive.count { classifier.classify(it.meditationText).coercive }, coercive.size)}\n")
            append("  오탐률(정통)           : ${rate(orthodox.count { classifier.classify(it.meditationText).coercive }, orthodox.size)}\n\n")
            append("-- 홀드아웃 반분(클래스별 id 정렬 후 짝수=fit) --\n")
            listOf(true to "fit", false to "holdout").forEach { (inFit, name) ->
                val c = half(coercive, inFit)
                val o = half(orthodox, inFit)
                append("  %-8s recall=%s  오탐률=%s\n".format(
                    name,
                    rate(c.count { classifier.classify(it.meditationText).coercive }, c.size),
                    rate(o.count { classifier.classify(it.meditationText).coercive }, o.size),
                ))
            }
            append("\n-- 걸린 관계(깊이 = 서로 다른 관계 수) --\n")
            coercive.forEach { fx ->
                val v = classifier.classify(fx.meditationText)
                append(
                    "  %-34s %s %s\n".format(
                        fx.id,
                        if (v.coercive) "깊이=${v.depth}" else "MISS   ",
                        v.relations.map { it.relation.label }.distinct().joinToString(","),
                    ),
                )
            }
            append("\n-- 정통 표본(전부 비어 있어야 한다) --\n")
            orthodox.forEach { fx ->
                val v = classifier.classify(fx.meditationText)
                if (v.coercive) {
                    append("  ⚠️ %-34s %s | %s\n".format(fx.id, v.relations.map { it.relation.label }.distinct(), v.relations.first().evidence))
                }
            }
            append("\n-- 부수 탐지(영지주의·뉴에이지: L1/L2 담당이라 요구사항 아님) --\n")
            listOf(GNOSTIC_CLASS, NEWAGE_CLASS).forEach { cls ->
                val f = signedOff(cls)
                append("  %-10s %s\n".format(cls, rate(f.count { classifier.classify(it.meditationText).coercive }, f.size)))
            }
        }
    }

    private companion object {
        const val COERCIVE_CLASS = "suffering-justification"
        const val ORTHODOX_CLASS = "orthodox"
        const val GNOSTIC_CLASS = "gnostic"
        const val NEWAGE_CLASS = "newage"

        /** `ruleIdOf(BLAME)` 의 고정값. 바뀌면 메트릭 시계열이 끊긴다. */
        const val BLAME_RULE_ID = "bf00a0276abe"

        /**
         * L3a 가 못 잡는 강제 구조 표본. **빈 목록이 목표가 아니다** — 목록을 비우려고 규칙을
         * 표본에 맞춰 좁히면 그건 표층형 lint 로 되돌아가는 길이다.
         */
        val MISSED_BY_L3A = emptyList<String>()
    }
}
