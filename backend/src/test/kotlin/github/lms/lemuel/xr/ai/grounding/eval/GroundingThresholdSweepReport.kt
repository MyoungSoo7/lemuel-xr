package github.lms.lemuel.xr.ai.grounding.eval

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase
import github.lms.lemuel.xr.ai.grounding.application.MemoizingEmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.SentenceSplitter
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingVerdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.math.roundToInt

/**
 * **임계치 스윕 리포트** — 근거성 게이트의 precision·recall 을 임계치 격자 위에서 측정한다.
 *
 * 설계 스펙 §7 이 예고한 전환("정통 ACCEPTED / 적대 REJECTED 분리가 안 되면 이 테스트를 임계치 스윕
 * 리포트로 바꾸고 precision/recall 을 기록한다")의 구현이다. 다만 분리 실패를 기다리지 않고 먼저 만든다 —
 * 임계치가 **왜** 그 값인지에 대한 근거가 지금은 픽스처 5개의 기억뿐이기 때문이다.
 *
 * ## 이것은 게이트가 아니라 리포트다
 * 여기서는 precision/recall 하한을 **의도적으로 강제하지 않는다.** signed_off 표본이 7건뿐이라
 * 어떤 수치 하한도 과적합을 고정하는 것 이상이 못 된다. 회귀 차단(직전 런 대비 악화 시 실패)은
 * 표본을 목표치까지 채운 뒤 Phase 4 에서 붙인다. 지금 assert 하는 건 *리포트 자체가 망가지지 않았는지*뿐이다.
 *
 * ## 비용
 * [MemoizingEmbeddingPort] 덕분에 임베딩은 **고유 텍스트당 1회**다. 격자를 아무리 키워도 API 호출은 늘지 않는다.
 *
 * 산출물: `build/reports/grounding-eval/latest.json` (+ 타임스탬프 사본) · 콘솔 요약표.
 * Phase 3 에서 이 JSON 을 CronJob 이 읽어 Prometheus gauge 로 내보낸다.
 */
@Tag("live-embedding")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GroundingThresholdSweepReport {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `sweep similarity and unsupported-rate thresholds over the golden set`() {
        val dataset = ClasspathGoldenSetAdapter(mapper).load(GoldenSet.DEFAULT_VERSION)
        val model = System.getenv("GROUNDING_EMBEDDING_MODEL")
            ?: dataset.manifest.tunedAgainst.embeddingModel.ifBlank { "gemini-embedding-001" }

        // 차원도 모델과 같은 방식으로 덮어쓸 수 있어야 한다. README §6.1 의 유사도 수치는
        // 3072 차원(어댑터 기본값이던 시절)에서 잰 것이고, 2026-08-12 #37 이후 런타임은 1536 이다.
        // 옛 수치와 대조하려면 같은 공간에서 다시 재는 길이 있어야 한다 —
        // 없으면 "코드가 바뀐 탓" 과 "모델이 바뀐 탓" 을 영영 못 가른다.
        val dimensionOverride = System.getenv("GROUNDING_EMBEDDING_DIMENSIONS")?.toInt()

        val embeddings = MemoizingEmbeddingPort(
            if (dimensionOverride == null) {
                GeminiEmbeddingAdapter(apiKey = System.getenv("GEMINI_API_KEY"), model = model)
            } else {
                GeminiEmbeddingAdapter(
                    apiKey = System.getenv("GEMINI_API_KEY"),
                    model = model,
                    outputDimensionality = dimensionOverride,
                )
            },
        )
        val useCase = EvaluateGroundingUseCase(embeddings, NOOP_METRICS)

        // 워밍업 — 격자를 돌기 전에 고유 텍스트를 한 번에 임베딩한다. 여기서 실패하면
        // 스윕 도중이 아니라 즉시 죽어서 원인이 분명해진다.
        val corpus = dataset.fixtures.flatMap { SentenceSplitter.split(it.meditationText) + it.texts() }.distinct()
        embeddings.embed(corpus)
        val dimensions = if (corpus.isEmpty()) 0 else embeddings.embed(listOf(corpus.first())).first().size

        val signedOff = dataset.signedOff
        val drafts = dataset.drafts
        val cells = grid().map { policy ->
            Cell(
                policy = policy,
                signedOff = EvalMetrics.summarize(score(useCase, signedOff, policy).map { it.toOutcome() }),
                draft = EvalMetrics.summarize(score(useCase, drafts, policy).map { it.toOutcome() }),
            )
        }

        val pinnedPolicy = dataset.manifest.pinnedPolicy.toPolicy()
        val pinnedScored = score(useCase, dataset.fixtures, pinnedPolicy)
        val pinnedSignedOff = EvalMetrics.summarize(
            pinnedScored.filter { it.fixture.signedOff }.map { it.toOutcome() },
        )

        // P3(오탐률 ≤ 5%) 를 만족하는 셀 중 recall 이 가장 높은 것 = 계약을 지키면서 가장 많이 거르는 설정.
        val p3Compliant = cells.filter { (it.signedOff.binary.p3FalseRejectRate ?: 1.0) <= P3_MAX_FALSE_REJECT }
        val bestUnderP3 = p3Compliant.maxWithOrNull(
            compareBy({ it.signedOff.binary.recall ?: -1.0 }, { it.signedOff.binary.f1 ?: -1.0 }),
        )
        val bestByF1 = cells.maxWithOrNull(compareBy { it.signedOff.binary.f1 ?: -1.0 })

        // ── 홀드아웃 ────────────────────────────────────────────────────────────────
        // 위 bestUnderP3/bestByF1 은 **인샘플** 값이다. 같은 표본에서 임계치를 고르고 같은 표본으로
        // 그 임계치를 자랑하면 수치가 반드시 좋아 보인다 — 이 리포에는 이미 그 전례가 있다
        // (GoldenSetTokenLintCrossCheckTest: 인샘플 7/7 대 아웃오브샘플 0/27).
        // 그래서 클래스별로 반씩 갈라 fit 에서만 고르고 holdout 에서 잰다. 분할은 id 정렬 후
        // 짝/홀 — 난수가 아니므로 같은 데이터면 언제 돌려도 같은 분할이 나오고, 분할을 여러 번
        // 바꿔 가며 좋은 쪽을 고르는 일(그것도 과적합이다)이 애초에 불가능하다.
        val (fitSet, holdoutSet) = stratifiedHalves(signedOff)
        val fitCells = grid().map { policy ->
            policy to EvalMetrics.summarize(score(useCase, fitSet, policy).map { it.toOutcome() })
        }
        val fitBestUnderP3 = fitCells
            .filter { (it.second.binary.p3FalseRejectRate ?: 1.0) <= P3_MAX_FALSE_REJECT }
            .maxWithOrNull(compareBy({ it.second.binary.recall ?: -1.0 }, { it.second.binary.f1 ?: -1.0 }))
        val fitBestByF1 = fitCells.maxWithOrNull(compareBy { it.second.binary.f1 ?: -1.0 })
        fun onHoldout(p: GroundingPolicy?) =
            p?.let { EvalMetrics.summarize(score(useCase, holdoutSet, it).map { it.toOutcome() }) }
        val holdout = mapOf(
            "split" to mapOf(
                "method" to "클래스별 id 정렬 후 짝수=fit / 홀수=holdout. 결정적이라 재분할로 고르는 과적합이 불가능하다.",
                "fit" to fitSet.map { it.id }.sorted(),
                "holdout" to holdoutSet.map { it.id }.sorted(),
            ),
            "pinnedOnHoldout" to onHoldout(pinnedPolicy),
            "fitBestUnderP3" to fitBestUnderP3?.let {
                mapOf("policy" to policyMap(it.first), "onFit" to it.second, "onHoldout" to onHoldout(it.first))
            },
            "fitBestByF1" to fitBestByF1?.let {
                mapOf("policy" to policyMap(it.first), "onFit" to it.second, "onHoldout" to onHoldout(it.first))
            },
        )

        val targets = dataset.manifest.targets
        val warnings = buildList {
            if (signedOff.size < targets.minSignedOff) {
                add(
                    "표본 부족: signed_off ${signedOff.size} < 목표 ${targets.minSignedOff}. " +
                        "아래 precision/recall 은 방향 지표이며 승격(P3/P4) 근거로 쓸 수 없다.",
                )
            }
            pinnedSignedOff.perClass
                .filter { it.n < targets.minPerClassSignedOff }
                .forEach { add("클래스 '${it.className}' 표본 ${it.n} < 목표 ${targets.minPerClassSignedOff}") }
            if (pinnedSignedOff.binary.abstained > 0) {
                add("기권 ${pinnedSignedOff.binary.abstained}건 — 임베딩 실패 가능성. 지표 신뢰도 확인 필요.")
            }
        }

        val report = mapOf(
            "generatedAt" to Instant.now().toString(),
            "dataset" to mapOf(
                "id" to dataset.manifest.dataset,
                "version" to dataset.manifest.version,
                "fixtures" to dataset.fixtures.size,
                "signedOff" to signedOff.size,
                "draft" to drafts.size,
            ),
            "embedding" to mapOf(
                "model" to model,
                "dimensions" to dimensions,
                "uniqueTextsEmbedded" to embeddings.embeddedTexts,
                "note" to "임계치 격자 크기와 무관하게 고유 텍스트당 1회만 호출한다(MemoizingEmbeddingPort).",
            ),
            "warnings" to warnings,
            "pinnedPolicy" to policyMap(pinnedPolicy),
            "pinned" to mapOf(
                "signedOff" to pinnedSignedOff,
                "draft" to EvalMetrics.summarize(
                    pinnedScored.filterNot { it.fixture.signedOff }.map { it.toOutcome() },
                ),
            ),
            "bestUnderP3" to bestUnderP3?.let { cellMap(it) },
            "bestByF1" to bestByF1?.let { cellMap(it) },
            "holdout" to holdout,
            "sweep" to cells.map { cellMap(it) },
            "perFixture" to pinnedScored.map { s ->
                mapOf(
                    "id" to s.fixture.id,
                    "class" to s.fixture.`class`,
                    "difficulty" to s.fixture.difficulty,
                    "review" to s.fixture.review.status,
                    "expected" to s.fixture.expectedStatus,
                    "actual" to s.verdict.status.name,
                    "unsupportedRate" to s.verdict.unsupportedRate,
                    "sentences" to s.verdict.sentenceResults.map {
                        mapOf(
                            "sentence" to it.sentence,
                            "maxSimilarity" to it.maxSimilarity,
                            "grounded" to it.grounded,
                            "bestPassageRef" to it.bestPassageRef,
                        )
                    },
                )
            },
        )

        val outDir = reportDir()
        Files.createDirectories(outDir)
        val writer = mapper.writerWithDefaultPrettyPrinter()
        val stamped = outDir.resolve("sweep-${Instant.now().toEpochMilli()}.json")
        writer.writeValue(stamped.toFile(), report)
        writer.writeValue(outDir.resolve("latest.json").toFile(), report)

        println(console(dataset, model, warnings, pinnedPolicy, pinnedSignedOff, bestUnderP3, bestByF1, stamped))
        println(
            buildString {
                append("\n-- 홀드아웃(fit ${fitSet.size} / holdout ${holdoutSet.size}, 클래스별 반분) --\n")
                append("  fit 에서 고른 P3 최적: ${fitBestUnderP3?.first?.let { "sim=%.2f maxUnsup=%.1f".format(it.similarityThreshold, it.maxUnsupportedRate) } ?: "없음"}\n")
                fitBestUnderP3?.let {
                    append("    on fit    : ${oneLine(it.second)}\n")
                    append("    on holdout: ${oneLine(onHoldout(it.first))}\n")
                }
                append("  fit 에서 고른 F1 최적: ${fitBestByF1?.first?.let { "sim=%.2f maxUnsup=%.1f".format(it.similarityThreshold, it.maxUnsupportedRate) } ?: "없음"}\n")
                fitBestByF1?.let {
                    append("    on fit    : ${oneLine(it.second)}\n")
                    append("    on holdout: ${oneLine(onHoldout(it.first))}\n")
                }
                append("  현행 고정정책 on holdout: ${oneLine(onHoldout(pinnedPolicy))}\n")
            },
        )

        // 리포트가 스스로 망가지지 않았는지만 검증한다 (수치 하한은 Phase 4).
        assertThat(dimensions).describedAs("임베딩 차원이 0 — 워밍업이 실패했다").isGreaterThan(0)
        assertThat(pinnedScored).hasSameSizeAs(dataset.fixtures)
        assertThat(cells).describedAs("스윕 격자가 비었다").isNotEmpty()
        assertThat(Files.exists(stamped)).describedAs("리포트 파일이 안 쓰였다: $stamped").isTrue()
    }

    private data class Cell(
        val policy: GroundingPolicy,
        val signedOff: EvalMetrics.Summary,
        val draft: EvalMetrics.Summary,
    )

    private fun policyMap(p: GroundingPolicy) = mapOf(
        "similarityThreshold" to p.similarityThreshold,
        "maxUnsupportedRate" to p.maxUnsupportedRate,
    )

    private fun cellMap(c: Cell) = mapOf(
        "policy" to policyMap(c.policy),
        "signedOff" to c.signedOff,
        "draft" to c.draft,
    )

    /** 임계치 격자. 임베딩이 캐시되므로 촘촘해도 비용이 늘지 않는다. */
    private fun grid(): List<GroundingPolicy> {
        val similarity = generateSequence(0.50) { it + 0.02 }.takeWhile { it <= 0.8001 }.map { it.round3() }
        val unsupported = generateSequence(0.0) { it + 0.1 }.takeWhile { it <= 1.0001 }.map { it.round3() }.toList()
        return similarity.flatMap { s -> unsupported.asSequence().map { u -> GroundingPolicy(s, u) } }.toList()
    }

    private fun Double.round3() = (this * 1000).roundToInt() / 1000.0

    /** Gradle Test 태스크의 작업 디렉터리는 백엔드 모듈 루트다. */
    private fun reportDir(): Path = Path.of("build", "reports", "grounding-eval")

    private fun console(
        dataset: GoldenSet.Loaded,
        model: String,
        warnings: List<String>,
        pinned: GroundingPolicy,
        pinnedSummary: EvalMetrics.Summary,
        bestUnderP3: Cell?,
        bestByF1: Cell?,
        stamped: Path,
    ): String = buildString {
        append("\n=== grounding threshold sweep — ${dataset.manifest.dataset} ${dataset.manifest.version} ===\n")
        append("embedding=$model  fixtures=${dataset.fixtures.size} (signed_off=${dataset.signedOff.size}, draft=${dataset.drafts.size})\n")
        warnings.forEach { append("⚠️  $it\n") }
        append("\n-- signed_off 표본, 고정 임계치 sim=${pinned.similarityThreshold} maxUnsup=${pinned.maxUnsupportedRate} --\n")
        append(binaryLine(pinnedSummary))
        append("클래스별 정확도\n")
        pinnedSummary.perClass.forEach { c ->
            append(
                "  %-26s n=%-3d acc=%s%s\n".format(
                    c.className, c.n, pct(c.accuracy),
                    if (c.misses.isEmpty()) "" else "  MISS=${c.misses.joinToString(",")}",
                ),
            )
        }
        append("\n-- 격자 최적 --\n")
        append("  P3(오탐률≤${pct(P3_MAX_FALSE_REJECT)}) 만족 중 recall 최대: ${cellLine(bestUnderP3)}\n")
        append("  F1 최대                                : ${cellLine(bestByF1)}\n")
        append("\nreport: $stamped\n")
    }

    private fun binaryLine(s: EvalMetrics.Summary): String {
        val b = s.binary
        return "  TP=${b.tp} FP=${b.fp} FN=${b.fn} TN=${b.tn} 기권=${b.abstained}\n" +
            "  precision=${pct(b.precision)}  recall=${pct(b.recall)}  F1=${pct(b.f1)}\n" +
            "  P3오탐률(FP/(TP+FP))=${pct(b.p3FalseRejectRate)}  FPR(FP/(FP+TN))=${pct(b.falsePositiveRate)}\n" +
            "  전체 상태일치 정확도=${pct(s.exactAccuracy)} (표본 ${s.sampleCount})\n"
    }

    private fun cellLine(c: Cell?): String = c?.let {
        "sim=%.2f maxUnsup=%.1f → precision=%s recall=%s F1=%s".format(
            it.policy.similarityThreshold, it.policy.maxUnsupportedRate,
            pct(it.signedOff.binary.precision), pct(it.signedOff.binary.recall), pct(it.signedOff.binary.f1),
        )
    } ?: "없음"

    private fun pct(v: Double?): String = v?.let { "%.1f%%".format(it * 100) } ?: "n/a"

    private fun oneLine(s: EvalMetrics.Summary?): String = s?.let {
        val b = it.binary
        "n=%d precision=%s recall=%s F1=%s P3=%s (TP=%d FP=%d FN=%d TN=%d)".format(
            it.sampleCount, pct(b.precision), pct(b.recall), pct(b.f1), pct(b.p3FalseRejectRate),
            b.tp, b.fp, b.fn, b.tn,
        )
    } ?: "없음"

    /**
     * 클래스별로 id 를 정렬해 짝수 인덱스를 fit, 홀수를 holdout 으로 가른다.
     *
     * 층화(stratified)인 이유: 그냥 반으로 자르면 orthodox 가 한쪽에 몰릴 수 있고, 그러면
     * P3(오탐률)이 한쪽에서만 정의돼 홀드아웃 수치가 무의미해진다.
     */
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

    /** 픽스처를 게이트에 통과시켜 채점 결과로 바꾼다. */
    private fun score(
        useCase: EvaluateGroundingUseCase,
        fixtures: List<GoldenSet.Fixture>,
        policy: GroundingPolicy,
    ): List<Scored> = fixtures.map { fx ->
        Scored(fx, useCase.evaluate(fx.purpose, fx.meditationText, fx.passages, policy))
    }

    private data class Scored(val fixture: GoldenSet.Fixture, val verdict: GroundingVerdict) {
        fun toOutcome() = EvalMetrics.Outcome(
            id = fixture.id,
            className = fixture.`class`,
            difficulty = fixture.difficulty,
            reviewStatus = fixture.reviewStatus,
            expected = fixture.expected,
            actual = verdict.status,
            unsupportedRate = verdict.unsupportedRate,
        )
    }

    private companion object {
        /** 승격계약 §2 P3 의 오탐률 상한. */
        const val P3_MAX_FALSE_REJECT = 0.05

        /** 평가 harness 는 게이트의 운영 메트릭을 오염시키면 안 된다 — 전부 버리는 포트. */
        val NOOP_METRICS = object : GroundingMetricsPort {
            override fun evaluated(purpose: String) = Unit
            override fun rejected(purpose: String) = Unit
            override fun unsupportedRate(purpose: String, rate: Double) = Unit
            override fun inconclusive(purpose: String) = Unit
        }
    }
}
