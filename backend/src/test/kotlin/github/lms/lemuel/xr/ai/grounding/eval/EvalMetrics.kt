package github.lms.lemuel.xr.ai.grounding.eval

import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus

/**
 * 골든셋 판정 결과 → 정확도·재현율 집계. **순수 함수**(네트워크·임베딩 무접촉)라
 * CI 에서 항상 검증된다([EvalMetricsTest]).
 *
 * ## 양성 클래스는 REJECTED 다
 * 게이트의 존재 이유가 "걸러야 할 것을 거른다"이므로 REJECTED 를 양성으로 둔다.
 *  - TP = 걸러야 할 걸 걸렀다   · FP = 멀쩡한 걸 걸렀다(**오탐**)
 *  - FN = 걸러야 할 걸 놓쳤다   · TN = 멀쩡한 걸 통과시켰다
 *
 * ## 오탐률이 두 개인 이유
 * 승격계약 §2 P3 은 "**reject 표본을** 사람이 검수해 실제로는 근거 있는 비율 < 5%" 로 적혀 있다.
 * 이건 통계학의 FPR 이 아니라 `FP/(TP+FP)` = `1 - precision` 이다. 둘을 같은 이름으로 부르면
 * 승격 판단이 조용히 틀어지므로 이름을 갈라 둘 다 낸다:
 *  - [Binary.p3FalseRejectRate] = FP/(TP+FP) — **P3 이 말하는 그 값**
 *  - [Binary.falsePositiveRate] = FP/(FP+TN) — 통계적 FPR
 *
 * ## 기권(abstain)을 따로 세는 이유
 * 기대가 ACCEPTED/REJECTED 인데 판정이 INCONCLUSIVE/NO_EVIDENCE 로 나오면 TP/FP/FN/TN 어디에도
 * 넣을 수 없다. 그냥 빼 버리면 임베딩이 전부 실패해 아무것도 판정 못 하는 게이트가
 * precision 1.0 으로 보인다. 그래서 [Binary.abstained] 로 세고 리포트에 항상 노출한다.
 */
object EvalMetrics {

    /** 픽스처 1건의 채점 결과. */
    data class Outcome(
        val id: String,
        val className: String,
        val difficulty: String,
        val reviewStatus: GroundingDataset.ReviewStatus,
        val expected: GroundingStatus,
        val actual: GroundingStatus,
        val unsupportedRate: Double,
    ) {
        val correct: Boolean get() = expected == actual
    }

    data class Binary(
        val tp: Int,
        val fp: Int,
        val fn: Int,
        val tn: Int,
        val abstained: Int,
    ) {
        val decided: Int get() = tp + fp + fn + tn
        val total: Int get() = decided + abstained

        /** TP/(TP+FP). 분모 0 이면 null — 0.0 으로 뭉개면 "완벽" 과 "표본 없음" 이 구분되지 않는다. */
        val precision: Double? get() = ratio(tp, tp + fp)

        /** TP/(TP+FN). */
        val recall: Double? get() = ratio(tp, tp + fn)

        val f1: Double?
            get() {
                val p = precision ?: return null
                val r = recall ?: return null
                return if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
            }

        /** FP/(TP+FP) = 1 − precision. **승격조건 P3 이 가리키는 값** (reject 표본 기준). */
        val p3FalseRejectRate: Double? get() = ratio(fp, tp + fp)

        /** FP/(FP+TN). 통계적 FPR — 통과해야 할 표본 중 잘못 걸린 비율. */
        val falsePositiveRate: Double? get() = ratio(fp, fp + tn)

        /** 기권을 뺀 판정 정확도. */
        val accuracy: Double? get() = ratio(tp + tn, decided)

        /** 전체 표본 대비 기권 비율 — 높으면 위 지표들을 신뢰하면 안 된다. */
        val abstainRate: Double? get() = ratio(abstained, total)
    }

    /** 클래스(층) 단위 집계. 전체 평균 하나로 뭉치면 회색지대 층이 망가져도 안 보인다. */
    data class ClassStats(
        val className: String,
        val n: Int,
        val exactMatches: Int,
        val misses: List<String>,
    ) {
        val accuracy: Double? get() = ratio(exactMatches, n)
    }

    data class Summary(
        val sampleCount: Int,
        val binary: Binary,
        val perClass: List<ClassStats>,
        /** 기대≠판정인 모든 픽스처 id (구조적 클래스 포함). */
        val mismatches: List<String>,
    ) {
        val exactAccuracy: Double? get() = ratio(sampleCount - mismatches.size, sampleCount)
    }

    fun summarize(outcomes: List<Outcome>): Summary {
        var tp = 0
        var fp = 0
        var fn = 0
        var tn = 0
        var abstained = 0

        outcomes.forEach { o ->
            val expectedBinary = o.expected.isBinary()
            if (!expectedBinary) return@forEach
            if (!o.actual.isBinary()) {
                abstained++
                return@forEach
            }
            val positiveExpected = o.expected == GroundingStatus.REJECTED
            val positiveActual = o.actual == GroundingStatus.REJECTED
            when {
                positiveExpected && positiveActual -> tp++
                !positiveExpected && positiveActual -> fp++
                positiveExpected && !positiveActual -> fn++
                else -> tn++
            }
        }

        val perClass = outcomes.groupBy { it.className }
            .toSortedMap()
            .map { (name, group) ->
                ClassStats(
                    className = name,
                    n = group.size,
                    exactMatches = group.count { it.correct },
                    misses = group.filterNot { it.correct }.map { it.id }.sorted(),
                )
            }

        return Summary(
            sampleCount = outcomes.size,
            binary = Binary(tp, fp, fn, tn, abstained),
            perClass = perClass,
            mismatches = outcomes.filterNot { it.correct }.map { it.id }.sorted(),
        )
    }

    /** ACCEPTED/REJECTED 만 이진 판정에 들어간다. NO_EVIDENCE·INCONCLUSIVE 는 기권. */
    private fun GroundingStatus.isBinary(): Boolean =
        this == GroundingStatus.ACCEPTED || this == GroundingStatus.REJECTED

    private fun ratio(numerator: Int, denominator: Int): Double? =
        if (denominator == 0) null else numerator.toDouble() / denominator
}
