package github.lms.lemuel.xr.ai.adapter.out.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * MicrometerLlmMetricsAdapter 단위 테스트 — 카운터 이름·태그·증가와 provider null 기본값 분기 커버.
 */
class MicrometerLlmMetricsAdapterTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var adapter: MicrometerLlmMetricsAdapter

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        adapter = MicrometerLlmMetricsAdapter(registry)
    }

    @Test
    fun `generationDisabled 는 purpose 태그로 카운터 증가`() {
        adapter.generationDisabled("diary")

        val c = registry.get("llm.generation.disabled").tag("purpose", "diary").counter()
        assertThat(c.count()).isEqualTo(1.0)
    }

    @Test
    fun `cacheHit 는 purpose 태그로 카운터 증가`() {
        adapter.cacheHit("diary")
        adapter.cacheHit("diary")

        val c = registry.get("llm.cache.hit").tag("purpose", "diary").counter()
        assertThat(c.count()).isEqualTo(2.0)
    }

    @Test
    fun `cacheMiss 는 purpose와 provider 태그로 카운터 증가`() {
        adapter.cacheMiss("diary", "openai")

        val c = registry.get("llm.cache.miss")
            .tag("purpose", "diary").tag("provider", "openai").counter()
        assertThat(c.count()).isEqualTo(1.0)
    }

    @Test
    fun `cacheMiss provider null이면 unknown 태그`() {
        adapter.cacheMiss("diary", null)

        val c = registry.get("llm.cache.miss")
            .tag("purpose", "diary").tag("provider", "unknown").counter()
        assertThat(c.count()).isEqualTo(1.0)
    }
}
