package github.lms.lemuel.xr.ai.grounding.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GroundingModelsTest {

    @Test
    fun `verdictStatus rejects when rate exceeds max`() {
        val policy = GroundingPolicy(similarityThreshold = 0.7, maxUnsupportedRate = 0.34)
        assertThat(policy.verdictStatus(0.5)).isEqualTo(GroundingStatus.REJECTED)
    }

    @Test
    fun `verdictStatus accepts at or below max`() {
        val policy = GroundingPolicy(similarityThreshold = 0.7, maxUnsupportedRate = 0.34)
        assertThat(policy.verdictStatus(0.34)).isEqualTo(GroundingStatus.ACCEPTED)
        assertThat(policy.verdictStatus(0.0)).isEqualTo(GroundingStatus.ACCEPTED)
    }

    @Test
    fun `accepted convenience reflects status`() {
        val v = GroundingVerdict(GroundingStatus.ACCEPTED, 0.0, emptyList(), 0.7)
        assertThat(v.accepted).isTrue()
        assertThat(v.copy(status = GroundingStatus.REJECTED).accepted).isFalse()
    }
}
