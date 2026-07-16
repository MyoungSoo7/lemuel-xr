package github.lms.lemuel.xr.ai.adapter.out.metrics

import github.lms.lemuel.xr.ai.application.port.out.LlmMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * [LlmMetricsPort] 구현 — Micrometer [MeterRegistry] 로 LLM 캐시/생성 카운터를 발행한다.
 *
 * 메트릭 이름·태그·카운트는 리팩터 이전 `GenerateLlmResponseUseCase` 인라인 코드와 동일:
 * `llm.generation.disabled{purpose}` / `llm.cache.hit{purpose}` /
 * `llm.cache.miss{purpose,provider}` (provider null → "unknown").
 */
@Component
class MicrometerLlmMetricsAdapter(
    private val meter: MeterRegistry,
) : LlmMetricsPort {

    override fun generationDisabled(purpose: String) {
        Counter.builder("llm.generation.disabled").tag("purpose", purpose).register(meter).increment()
    }

    override fun cacheHit(purpose: String) {
        Counter.builder("llm.cache.hit").tag("purpose", purpose).register(meter).increment()
    }

    override fun cacheMiss(purpose: String, provider: String?) {
        Counter.builder("llm.cache.miss")
            .tag("purpose", purpose)
            .tag("provider", provider ?: "unknown")
            .register(meter).increment()
    }
}
