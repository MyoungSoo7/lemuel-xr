package github.lms.lemuel.xr.recovery.adapter.out.persistence

import github.lms.lemuel.xr.recovery.application.port.out.RecoveryMetricPort
import github.lms.lemuel.xr.recovery.domain.RecoveryMetric
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

/**
 * [RecoveryMetricPort] 구현 — Spring Data [RecoveryMetricJpaRepository] 위임.
 *
 * 엔티티↔도메인 매핑을 담당하는 유일한 지점. [RecoveryMetricJpaEntity] 를 import 하는
 * recovery 컨텍스트 내 유일한 클래스여야 한다.
 */
@Component
class RecoveryMetricPersistenceAdapter(
    private val repository: RecoveryMetricJpaRepository,
) : RecoveryMetricPort {

    override fun findRecent(userId: UUID, since: LocalDate): List<RecoveryMetric> =
        repository.findRecent(userId, since).map(::toDomain)

    override fun save(metric: RecoveryMetric): RecoveryMetric =
        toDomain(repository.save(toEntity(metric)))

    private fun toDomain(e: RecoveryMetricJpaEntity): RecoveryMetric =
        RecoveryMetric(
            id = e.id,
            userId = e.userId!!,
            date = e.metricDate!!,
            emotionDiversity = e.emotionDiversityCount,
            avgIntensity = e.avgIntensity,
            diaryWords = e.diaryWordCount,
            missionsCompleted = e.missionCompletedCount,
            topKeywords = e.topKeywords,
            riskSignals = e.riskSignalCount,
        )

    private fun toEntity(m: RecoveryMetric): RecoveryMetricJpaEntity =
        RecoveryMetricJpaEntity().apply {
            id = m.id
            userId = m.userId
            metricDate = m.date
            emotionDiversityCount = m.emotionDiversity
            avgIntensity = m.avgIntensity
            diaryWordCount = m.diaryWords
            missionCompletedCount = m.missionsCompleted
            topKeywords = m.topKeywords
            riskSignalCount = m.riskSignals
        }
}
