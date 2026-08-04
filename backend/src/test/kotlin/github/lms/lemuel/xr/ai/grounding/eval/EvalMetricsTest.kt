package github.lms.lemuel.xr.ai.grounding.eval

import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * 집계 산식 자체의 회귀 테스트. 네트워크·임베딩 무접촉이라 **CI 에서 항상 돈다**.
 *
 * 라이브 스윕 리포트는 `GEMINI_API_KEY` 없이는 skip 되므로, 산식이 조용히 틀어지면
 * 아무도 모르는 채 승격 판단만 잘못된다. 그 경로를 여기서 막는다.
 */
class EvalMetricsTest {

    @Test
    fun `완전 분리면 정밀도 재현율 1 이고 P3 오탐률은 0`() {
        val summary = EvalMetrics.summarize(
            listOf(
                outcome("r1", GroundingStatus.REJECTED, GroundingStatus.REJECTED),
                outcome("r2", GroundingStatus.REJECTED, GroundingStatus.REJECTED),
                outcome("a1", GroundingStatus.ACCEPTED, GroundingStatus.ACCEPTED),
            ),
        )
        val b = summary.binary
        assertThat(b.tp).isEqualTo(2)
        assertThat(b.tn).isEqualTo(1)
        assertThat(b.fp + b.fn + b.abstained).isEqualTo(0)
        assertThat(b.precision).isEqualTo(1.0)
        assertThat(b.recall).isEqualTo(1.0)
        assertThat(b.f1).isEqualTo(1.0)
        assertThat(b.p3FalseRejectRate).isEqualTo(0.0)
        assertThat(summary.exactAccuracy).isEqualTo(1.0)
        assertThat(summary.mismatches).isEmpty()
    }

    @Test
    fun `P3 오탐률과 통계적 FPR 은 서로 다른 값이다`() {
        // reject 판정 4건 중 1건이 실제로는 정통(=오탐), 통과해야 할 표본은 총 5건.
        val outcomes = buildList {
            repeat(3) { add(outcome("tp$it", GroundingStatus.REJECTED, GroundingStatus.REJECTED)) }
            add(outcome("fp1", GroundingStatus.ACCEPTED, GroundingStatus.REJECTED))
            repeat(4) { add(outcome("tn$it", GroundingStatus.ACCEPTED, GroundingStatus.ACCEPTED)) }
        }
        val b = EvalMetrics.summarize(outcomes).binary

        // P3 이 말하는 값: reject 표본 4건 중 1건 → 0.25
        assertThat(b.p3FalseRejectRate).isEqualTo(0.25)
        // 통계적 FPR: 통과해야 할 5건 중 1건 → 0.2. 두 값이 갈리는 게 이 테스트의 요점이다.
        assertThat(b.falsePositiveRate).isEqualTo(0.2)
        assertThat(b.p3FalseRejectRate).isNotEqualTo(b.falsePositiveRate)
        // 정의상 P3 오탐률 = 1 - precision 이어야 한다.
        assertThat(b.p3FalseRejectRate!!).isCloseTo(1 - b.precision!!, within(1e-12))
    }

    @Test
    fun `놓친 이단은 FN 으로 재현율을 깎는다`() {
        val b = EvalMetrics.summarize(
            listOf(
                outcome("tp", GroundingStatus.REJECTED, GroundingStatus.REJECTED),
                outcome("fn", GroundingStatus.REJECTED, GroundingStatus.ACCEPTED),
                outcome("tn", GroundingStatus.ACCEPTED, GroundingStatus.ACCEPTED),
            ),
        ).binary
        assertThat(b.fn).isEqualTo(1)
        assertThat(b.recall).isEqualTo(0.5)
        assertThat(b.precision).isEqualTo(1.0)
        assertThat(b.f1!!).isCloseTo(2 * 1.0 * 0.5 / 1.5, within(1e-12))
    }

    @Test
    fun `판정 불가는 기권으로 세고 이진 지표에 넣지 않는다`() {
        // 임베딩이 전부 실패해 INCONCLUSIVE 만 나오는 게이트가 "precision 1.0" 으로 보이면 안 된다.
        val summary = EvalMetrics.summarize(
            listOf(
                outcome("tp", GroundingStatus.REJECTED, GroundingStatus.REJECTED),
                outcome("dead1", GroundingStatus.REJECTED, GroundingStatus.INCONCLUSIVE),
                outcome("dead2", GroundingStatus.ACCEPTED, GroundingStatus.NO_EVIDENCE),
            ),
        )
        val b = summary.binary
        assertThat(b.abstained).isEqualTo(2)
        assertThat(b.decided).isEqualTo(1)
        assertThat(b.total).isEqualTo(3)
        assertThat(b.abstainRate!!).isCloseTo(2.0 / 3.0, within(1e-12))
        // 기권은 오답이므로 exact accuracy 는 떨어져야 한다 — 여기가 사망을 드러내는 지표.
        assertThat(summary.exactAccuracy!!).isCloseTo(1.0 / 3.0, within(1e-12))
        assertThat(summary.mismatches).containsExactly("dead1", "dead2")
    }

    @Test
    fun `구조적 상태가 기대값이면 이진 집계에서 아예 빠진다`() {
        // NO_EVIDENCE 를 기대하는 픽스처는 ACCEPTED/REJECTED 판단 대상이 아니다.
        // 기권(abstained)도 아니다 — 애초에 이진 문제가 아니기 때문.
        val summary = EvalMetrics.summarize(
            listOf(
                outcome("s1", GroundingStatus.NO_EVIDENCE, GroundingStatus.NO_EVIDENCE),
                outcome("s2", GroundingStatus.INCONCLUSIVE, GroundingStatus.INCONCLUSIVE),
                outcome("s3", GroundingStatus.NO_EVIDENCE, GroundingStatus.REJECTED),
            ),
        )
        val b = summary.binary
        assertThat(b.total).isEqualTo(0)
        assertThat(b.abstained).isEqualTo(0)
        // 그래도 exact accuracy 로는 잡혀야 한다 — s3 은 명백한 회귀다.
        assertThat(summary.mismatches).containsExactly("s3")
        assertThat(summary.exactAccuracy!!).isCloseTo(2.0 / 3.0, within(1e-12))
    }

    @Test
    fun `분모가 0 이면 0 이 아니라 null 이다`() {
        // "완벽함" 과 "측정할 표본이 없음" 이 같은 숫자로 보이면 승격 근거가 조작된다.
        val b = EvalMetrics.summarize(emptyList()).binary
        assertThat(b.precision).isNull()
        assertThat(b.recall).isNull()
        assertThat(b.f1).isNull()
        assertThat(b.p3FalseRejectRate).isNull()
        assertThat(b.falsePositiveRate).isNull()
        assertThat(b.accuracy).isNull()
        assertThat(b.abstainRate).isNull()
        assertThat(EvalMetrics.summarize(emptyList()).exactAccuracy).isNull()

        // reject 판정이 하나도 없으면 precision·P3 만 null 이고 recall 은 계산된다.
        val noRejects = EvalMetrics.summarize(
            listOf(outcome("fn", GroundingStatus.REJECTED, GroundingStatus.ACCEPTED)),
        ).binary
        assertThat(noRejects.precision).isNull()
        assertThat(noRejects.p3FalseRejectRate).isNull()
        assertThat(noRejects.recall).isEqualTo(0.0)
        assertThat(noRejects.f1).isNull()
    }

    @Test
    fun `클래스별 집계는 층마다 오답을 따로 드러낸다`() {
        val summary = EvalMetrics.summarize(
            listOf(
                outcome("g1", GroundingStatus.REJECTED, GroundingStatus.REJECTED, className = "gnostic"),
                outcome("g2", GroundingStatus.REJECTED, GroundingStatus.ACCEPTED, className = "gnostic"),
                outcome("o1", GroundingStatus.ACCEPTED, GroundingStatus.ACCEPTED, className = "orthodox"),
            ),
        )
        // 전체 정확도 2/3 하나만 보면 gnostic 층이 반쪽 났다는 사실이 묻힌다.
        assertThat(summary.perClass.map { it.className }).containsExactly("gnostic", "orthodox")
        val gnostic = summary.perClass.first { it.className == "gnostic" }
        assertThat(gnostic.n).isEqualTo(2)
        assertThat(gnostic.accuracy).isEqualTo(0.5)
        assertThat(gnostic.misses).containsExactly("g2")
        assertThat(summary.perClass.first { it.className == "orthodox" }.accuracy).isEqualTo(1.0)
    }

    private fun outcome(
        id: String,
        expected: GroundingStatus,
        actual: GroundingStatus,
        className: String = "test",
    ) = EvalMetrics.Outcome(
        id = id,
        className = className,
        difficulty = "medium",
        reviewStatus = GoldenSet.ReviewStatus.SIGNED_OFF,
        expected = expected,
        actual = actual,
        unsupportedRate = 0.0,
    )
}
