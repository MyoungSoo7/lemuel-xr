package github.lms.lemuel.xr.ai.grounding.adapter.out.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MicrometerGroundingMetricsAdapterTest {

    private val registry = SimpleMeterRegistry()
    private val adapter = MicrometerGroundingMetricsAdapter(registry)

    @Test
    fun `evaluated increments a purpose-tagged counter`() {
        adapter.evaluated("meditation")
        adapter.evaluated("meditation")
        assertThat(registry.get("grounding.evaluated").tag("purpose", "meditation").counter().count())
            .isEqualTo(2.0)
    }

    @Test
    fun `rejected and inconclusive increment their counters`() {
        adapter.rejected("meditation")
        adapter.inconclusive("scene")
        assertThat(registry.get("grounding.rejected").tag("purpose", "meditation").counter().count()).isEqualTo(1.0)
        assertThat(registry.get("grounding.inconclusive").tag("purpose", "scene").counter().count()).isEqualTo(1.0)
    }

    @Test
    fun `unsupportedRate records into a summary`() {
        adapter.unsupportedRate("meditation", 0.25)
        val summary = registry.get("grounding.unsupported_rate").tag("purpose", "meditation").summary()
        assertThat(summary.count()).isEqualTo(1)
        assertThat(summary.totalAmount()).isEqualTo(0.25)
    }
}
