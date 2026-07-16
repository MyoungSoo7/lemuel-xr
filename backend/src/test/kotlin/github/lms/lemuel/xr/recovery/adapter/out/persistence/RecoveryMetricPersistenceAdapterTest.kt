package github.lms.lemuel.xr.recovery.adapter.out.persistence

import github.lms.lemuel.xr.recovery.domain.RecoveryMetric
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * RecoveryMetricPersistenceAdapter 단위 테스트 — 도메인↔엔티티 왕복 매핑 커버.
 */
class RecoveryMetricPersistenceAdapterTest {

    private val repository: RecoveryMetricJpaRepository = mock()
    private val adapter = RecoveryMetricPersistenceAdapter(repository)

    private val user = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val date = LocalDate.of(2026, 7, 4)
    private val keywords = arrayOf("peace", "hope")

    private fun fullEntity(): RecoveryMetricJpaEntity {
        val e = RecoveryMetricJpaEntity()
        e.id = 9L
        e.userId = user
        e.metricDate = date
        e.emotionDiversityCount = 5
        e.avgIntensity = BigDecimal("6.4")
        e.diaryWordCount = 120
        e.missionCompletedCount = 3
        e.topKeywords = keywords
        e.riskSignalCount = 1
        return e
    }

    private fun fullDomain(): RecoveryMetric =
        RecoveryMetric(9L, user, date, 5, BigDecimal("6.4"), 120, 3, keywords, 1)

    @Test
    fun `save 는 toEntity 변환후 저장결과를 도메인으로 반환한다`() {
        whenever(repository.save(any())).thenReturn(fullEntity())

        val result = adapter.save(fullDomain())

        val captor = argumentCaptor<RecoveryMetricJpaEntity>()
        verify(repository).save(captor.capture())
        val persisted = captor.firstValue
        assertThat(persisted.id).isEqualTo(9L)
        assertThat(persisted.userId).isEqualTo(user)
        assertThat(persisted.metricDate).isEqualTo(date)
        assertThat(persisted.emotionDiversityCount).isEqualTo(5)
        assertThat(persisted.avgIntensity).isEqualByComparingTo("6.4")
        assertThat(persisted.diaryWordCount).isEqualTo(120)
        assertThat(persisted.missionCompletedCount).isEqualTo(3)
        assertThat(persisted.topKeywords).containsExactly("peace", "hope")
        assertThat(persisted.riskSignalCount).isEqualTo(1)

        assertThat(result.id).isEqualTo(9L)
        assertThat(result.userId).isEqualTo(user)
        assertThat(result.date).isEqualTo(date)
        assertThat(result.emotionDiversity).isEqualTo(5)
        assertThat(result.avgIntensity).isEqualByComparingTo("6.4")
        assertThat(result.diaryWords).isEqualTo(120)
        assertThat(result.missionsCompleted).isEqualTo(3)
        assertThat(result.topKeywords).containsExactly("peace", "hope")
        assertThat(result.riskSignals).isEqualTo(1)
    }

    @Test
    fun `findRecent 는 리스트를 도메인으로 매핑한다`() {
        whenever(repository.findRecent(user, date)).thenReturn(listOf(fullEntity()))

        val result = adapter.findRecent(user, date)

        assertThat(result).hasSize(1)
        assertThat(result[0].userId).isEqualTo(user)
        assertThat(result[0].topKeywords).containsExactly("peace", "hope")
    }
}
