package github.lms.lemuel.xr.ai.grounding.eval

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingVerdict

/** 골든셋 픽스처를 게이트에 통과시켜 채점 결과로 바꾼다. 스윕 리포트와 회귀 테스트가 공유한다. */
object GroundingScorer {

    /** 평가 harness 는 게이트의 운영 메트릭을 오염시키면 안 된다 — 전부 버리는 포트. */
    val NOOP_METRICS = object : GroundingMetricsPort {
        override fun evaluated(purpose: String) = Unit
        override fun rejected(purpose: String) = Unit
        override fun unsupportedRate(purpose: String, rate: Double) = Unit
        override fun inconclusive(purpose: String) = Unit
    }

    data class Scored(val fixture: GroundingDataset.Fixture, val verdict: GroundingVerdict) {
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

    fun score(
        useCase: EvaluateGroundingUseCase,
        fixtures: List<GroundingDataset.Fixture>,
        policy: GroundingPolicy,
    ): List<Scored> = fixtures.map { fx ->
        Scored(fx, useCase.evaluate(fx.purpose, fx.meditationText, fx.passages, policy))
    }

    fun outcomes(
        useCase: EvaluateGroundingUseCase,
        fixtures: List<GroundingDataset.Fixture>,
        policy: GroundingPolicy,
    ): List<EvalMetrics.Outcome> = score(useCase, fixtures, policy).map { it.toOutcome() }
}
