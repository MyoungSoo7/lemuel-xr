package github.lms.lemuel.xr.ai.grounding.application.port.out

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGoldenSetUseCase

/**
 * 골든셋 채점 결과를 모니터링 시스템에 노출하는 out-port.
 * `grounding.*` 운영 메트릭과는 별개 계열(`grounding.goldenset.*`)이다 — 합성 트래픽이므로.
 */
interface GoldenSetEvalMetricsPort {

    /** 채점 성공. 지표를 갱신한다. */
    fun publish(result: EvaluateGoldenSetUseCase.Result)

    /**
     * 채점 실패(임베딩 API 장애 등). 실패를 세기만 하고 **지난 성공 지표는 건드리지 않는다** —
     * 마지막 성공 시각 지표가 늙는 것으로 장애가 드러난다.
     */
    fun failed()
}
