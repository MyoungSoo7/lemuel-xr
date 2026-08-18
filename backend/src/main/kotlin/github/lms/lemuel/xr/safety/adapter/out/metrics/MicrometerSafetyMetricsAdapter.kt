package github.lms.lemuel.xr.safety.adapter.out.metrics

import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import github.lms.lemuel.xr.safety.application.port.out.SafetyMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
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

    override fun recordGateBlock(axis: String, layer: String, ruleId: String) {
        gateBlock(axis, layer, ruleId).increment()
    }

    /**
     * 축×층 격자를 0 으로 미리 세운다.
     *
     * 카운터는 처음 증가할 때 생성되므로, 미리 세우지 않으면 **한 번도 안 걸린 축** 과
     * **게이트가 배포되지 않은 축** 이 대시보드에서 똑같이 "계열 없음" 으로 보인다.
     * 안전 게이트에서 그 둘을 못 가르면 침묵을 초록으로 읽게 된다 — 이 저장소가 반복해서
     * 막아 온 실패 형태다. 0 이 보이면 "재고 있고 아직 안 걸림" 이라고 읽을 수 있다.
     */
    @PostConstruct
    fun preRegisterGrid() {
        for (axis in listOf(ForbiddenTokenScanner.DEFAULT_AXIS, ForbiddenTokenScanner.THEOLOGY_AXIS)) {
            for (layer in listOf(SafetyMetricsPort.LAYER_LLM, SafetyMetricsPort.LAYER_CONTENT)) {
                gateBlock(axis, layer, SafetyMetricsPort.RULE_ID_NONE)
            }
        }
    }

    private fun gateBlock(axis: String, layer: String, ruleId: String): Counter =
        Counter.builder("content.gate.block")
            .tag("axis", axis)
            .tag("layer", layer)
            .tag("rule_id", ruleId)
            .register(meter)
}
