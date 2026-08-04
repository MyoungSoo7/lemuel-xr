package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.eval.EvalMetrics
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import org.slf4j.LoggerFactory

/**
 * 골든셋을 근거성 게이트에 통과시켜 정확도·재현율을 산출한다.
 *
 * 사용자 요청 경로를 전혀 건드리지 않는다 — 채점 대상은 고정된 픽스처뿐이다. 덕분에 게이트를
 * 실사용에 붙이기 **전에도** 프로덕션 환경에서 판별력을 관측할 수 있다(승격계약 §2 P3·P4 의 근거).
 *
 * ## 게이트의 운영 메트릭을 오염시키지 않는다
 * 채점은 합성 트래픽이다. 이걸 `grounding.evaluated`·`grounding.rejected` 에 섞으면
 * 실사용 reject 율(P6 드리프트 경보의 대상)이 골든셋 실행 주기에 맞춰 출렁인다.
 * 그래서 게이트에는 [GroundingMetricsPort] 무동작 구현을 주입한다.
 */
class EvaluateGoldenSetUseCase(
    private val embeddings: EmbeddingPort,
    private val goldenSet: GoldenSetPort,
    private val version: String = GoldenSet.DEFAULT_VERSION,
) {

    /** 한 번의 채점 결과. */
    data class Result(
        val version: String,
        val policy: GroundingPolicy,
        val summary: EvalMetrics.Summary,
        val outcomes: List<EvalMetrics.Outcome>,
        /** 이번 실행에서 실제 임베딩 API 를 태운 텍스트 수 — 비용 근거. */
        val embeddedTexts: Int,
        /** 라벨이 draft 라서 채점에서 제외된 픽스처 수. */
        val excludedDrafts: Int,
    )

    /**
     * @param policy 미지정 시 manifest 의 고정 정책(운영에서 쓰는 그 값).
     */
    fun run(policy: GroundingPolicy? = null): Result {
        val loaded = goldenSet.load(version)
        val effective = policy ?: loaded.manifest.pinnedPolicy.toPolicy()

        // 캐시는 이 실행 안에서만 산다. 실행 간에 살려 두면 임베딩 모델이 바뀌어도
        // 옛 벡터를 재사용해, 감시해야 할 드리프트를 스스로 가린다.
        val memoizing = MemoizingEmbeddingPort(embeddings)
        val gate = EvaluateGroundingUseCase(memoizing, NoOpGroundingMetrics)

        // draft 는 사람 사인오프 전이라 지표의 근거가 될 수 없다.
        val fixtures = loaded.signedOff
        val outcomes = fixtures.map { fx ->
            val verdict = gate.evaluate(fx.purpose, fx.meditationText, fx.passages, effective)
            EvalMetrics.Outcome(
                id = fx.id,
                className = fx.`class`,
                difficulty = fx.difficulty,
                reviewStatus = fx.reviewStatus,
                expected = fx.expected,
                actual = verdict.status,
                unsupportedRate = verdict.unsupportedRate,
            )
        }

        val result = Result(
            version = loaded.manifest.version.ifBlank { version },
            policy = effective,
            summary = EvalMetrics.summarize(outcomes),
            outcomes = outcomes,
            embeddedTexts = memoizing.embeddedTexts,
            excludedDrafts = loaded.drafts.size,
        )
        log.info(
            "골든셋 채점 완료 version={} n={} 불일치={} 기권={} 임베딩호출={} 정책=(sim={}, maxUnsup={})",
            result.version,
            result.summary.sampleCount,
            result.summary.mismatches.size,
            result.summary.binary.abstained,
            result.embeddedTexts,
            effective.similarityThreshold,
            effective.maxUnsupportedRate,
        )
        return result
    }

    private object NoOpGroundingMetrics : GroundingMetricsPort {
        override fun evaluated(purpose: String) = Unit
        override fun rejected(purpose: String) = Unit
        override fun inconclusive(purpose: String) = Unit
        override fun unsupportedRate(purpose: String, rate: Double) = Unit
    }

    private companion object {
        private val log = LoggerFactory.getLogger(EvaluateGoldenSetUseCase::class.java)
    }
}
