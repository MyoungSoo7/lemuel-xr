package github.lms.lemuel.xr.recovery.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * RecoveryMetric 도메인 테스트 — newMetric 팩토리가 id=null 로 신규 집계를 만들고
 * 나머지 필드를 그대로 매핑하는지 검증.
 */
class RecoveryMetricTest {

    @Test
    fun `newMetric id는 null이고 나머지 필드는 그대로 매핑`() {
        val userId = UUID.randomUUID()
        val date = LocalDate.of(2026, 7, 15)
        val keywords = arrayOf("gratitude", "hope")

        val m = RecoveryMetric.newMetric(
            userId, date, 4, BigDecimal("3.25"), 120, 2, keywords, 1,
        )

        assertThat(m.id).isNull()
        assertThat(m.userId).isEqualTo(userId)
        assertThat(m.date).isEqualTo(date)
        assertThat(m.emotionDiversity).isEqualTo(4)
        assertThat(m.avgIntensity).isEqualByComparingTo("3.25")
        assertThat(m.diaryWords).isEqualTo(120)
        assertThat(m.missionsCompleted).isEqualTo(2)
        assertThat(m.topKeywords).containsExactly("gratitude", "hope")
        assertThat(m.riskSignals).isEqualTo(1)
    }
}
