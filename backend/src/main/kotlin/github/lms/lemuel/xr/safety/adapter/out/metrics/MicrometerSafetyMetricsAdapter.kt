package github.lms.lemuel.xr.safety.adapter.out.metrics

import github.lms.lemuel.xr.safety.application.port.out.SafetyMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * [SafetyMetricsPort] 구현 — Micrometer [MeterRegistry] 로 안전 카운터를 발행한다.
 *
 * Micrometer 를 import 하는 유일한 지점. 메트릭 이름·태그·동작은 리팩터 이전과 동일하다.
 */
@Component
class MicrometerSafetyMetricsAdapter(
    private val meter: MeterRegistry,
) : SafetyMetricsPort {

    override fun recordAlert(severity: String?, source: String?) {
        Counter.builder("safety.alert")
            .tag("severity", severity ?: "unknown")
            .tag("source", source ?: "unknown")
            .register(meter).increment()
    }

    override fun recordSessionExit(reason: String?) {
        Counter.builder("safety.session.exit")
            .tag("reason", reason ?: "user_choice")
            .register(meter).increment()
    }
}
