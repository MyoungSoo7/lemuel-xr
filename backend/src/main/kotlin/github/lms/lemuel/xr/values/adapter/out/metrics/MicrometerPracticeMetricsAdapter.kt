package github.lms.lemuel.xr.values.adapter.out.metrics

import github.lms.lemuel.xr.values.application.port.out.PracticeMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/** PracticeMetricsPort 구현 — Micrometer MeterRegistry 로 실천 카운터를 발행한다. */
@Component
class MicrometerPracticeMetricsAdapter(
    private val meter: MeterRegistry,
) : PracticeMetricsPort {

    override fun recordPractice(valueId: Int, linkedCharacter: String?) {
        Counter.builder("values.practice")
            .tag("value_id", valueId.toString())
            .tag("linked_character", linkedCharacter ?: "none")
            .register(meter)
            .increment()
    }
}
