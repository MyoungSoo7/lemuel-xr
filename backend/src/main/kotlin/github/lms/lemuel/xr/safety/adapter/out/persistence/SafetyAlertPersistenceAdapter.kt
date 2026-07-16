package github.lms.lemuel.xr.safety.adapter.out.persistence

import github.lms.lemuel.xr.safety.application.port.out.SafetyAlertPort
import github.lms.lemuel.xr.safety.domain.SafetyAlert
import org.springframework.stereotype.Component

/**
 * [SafetyAlertPort] 구현 — Spring Data [SafetyAlertJpaRepository] 위임.
 *
 * [SafetyAlertJpaEntity] ↔ [SafetyAlert] 매핑을 담당하는 유일한 지점.
 * JPA 엔티티는 이 어댑터 밖으로 나가지 않는다.
 */
@Component
class SafetyAlertPersistenceAdapter(
    private val repository: SafetyAlertJpaRepository,
) : SafetyAlertPort {

    override fun save(alert: SafetyAlert): SafetyAlert =
        toDomain(repository.save(toEntity(alert)))

    private fun toEntity(d: SafetyAlert): SafetyAlertJpaEntity =
        SafetyAlertJpaEntity().apply {
            id = d.id
            userId = d.userId
            appSessionId = d.appSessionId
            emotionLogId = d.emotionLogId
            matchedPattern = d.matchedPattern
            severity = d.severity
            triggerSource = d.triggerSource
            rawExcerptHash = d.rawExcerptHash
            shownResources = d.shownResources
                ?.map { row -> row.filterValues { it != null }.mapValues { it.value!! } }
                ?.toMutableList()
            userAcknowledged = d.userAcknowledged
            createdAt = d.createdAt
        }

    private fun toDomain(e: SafetyAlertJpaEntity): SafetyAlert =
        SafetyAlert(
            id = e.id,
            userId = e.userId,
            appSessionId = e.appSessionId,
            emotionLogId = e.emotionLogId,
            matchedPattern = e.matchedPattern!!,
            severity = e.severity!!,
            triggerSource = e.triggerSource!!,
            rawExcerptHash = e.rawExcerptHash,
            shownResources = e.shownResources,
            userAcknowledged = e.userAcknowledged == true,
            createdAt = e.createdAt,
        )
}
