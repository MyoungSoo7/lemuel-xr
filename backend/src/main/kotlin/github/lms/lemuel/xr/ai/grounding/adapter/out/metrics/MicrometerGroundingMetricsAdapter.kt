package github.lms.lemuel.xr.ai.grounding.adapter.out.metrics

import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

/**
 * 근거성 게이트 메트릭 Micrometer 어댑터. Grafana 의 *AI 비용·캐시* row 와 나란히 두는
 * `grounding.*` 계열. use-case 는 이 구현을 모른다(DIP).
 *
 * 스프링 빈 아님(2026-07-19 결정): 섀도우 프로토타입에선 테스트가 SimpleMeterRegistry 로 직접 생성한다.
 */
class MicrometerGroundingMetricsAdapter(
    private val registry: MeterRegistry,
) : GroundingMetricsPort {

    override fun evaluated(purpose: String) {
        Counter.builder("grounding.evaluated").tag("purpose", purpose).register(registry).increment()
    }

    override fun rejected(purpose: String) {
        Counter.builder("grounding.rejected").tag("purpose", purpose).register(registry).increment()
    }

    override fun inconclusive(purpose: String) {
        Counter.builder("grounding.inconclusive").tag("purpose", purpose).register(registry).increment()
    }

    override fun unsupportedRate(purpose: String, rate: Double) {
        DistributionSummary.builder("grounding.unsupported_rate").tag("purpose", purpose)
            .register(registry).record(rate)
    }
}
