package github.lms.lemuel.xr.ai.grounding.application.port.out

/** 근거성 게이트 메트릭 발행 out-port — Micrometer 를 use-case 로부터 격리(DIP). */
interface GroundingMetricsPort {
    fun evaluated(purpose: String)
    fun rejected(purpose: String)
    fun unsupportedRate(purpose: String, rate: Double)
    fun inconclusive(purpose: String)
}
