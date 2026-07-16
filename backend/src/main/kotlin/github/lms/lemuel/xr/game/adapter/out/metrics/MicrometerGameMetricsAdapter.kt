package github.lms.lemuel.xr.game.adapter.out.metrics

import github.lms.lemuel.xr.game.application.port.out.GameMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * [GameMetricsPort] 의 Micrometer 구현 — game 컨텍스트에서 `MeterRegistry` 를
 * 참조하는 유일한 지점. 카운터 이름/태그는 종전 GameController 인라인 계측과 동일.
 */
@Component
class MicrometerGameMetricsAdapter(
    private val meter: MeterRegistry,
) : GameMetricsPort {

    override fun sessionStarted(character: String, mode: String?) {
        Counter.builder("game.session.started")
            .tag("character", character)
            .tag("mode", mode ?: "unspecified")
            .register(meter).increment()
    }

    override fun sessionCompleted(character: String) {
        Counter.builder("game.session.completed")
            .tag("character", character)
            .register(meter).increment()
    }
}
