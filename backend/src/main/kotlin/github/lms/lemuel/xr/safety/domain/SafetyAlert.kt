package github.lms.lemuel.xr.safety.domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * 안전 알림 도메인 모델 — 위기 키워드 매칭 시 기록되는 safety_alert 한 건.
 *
 * 영속화 세부(JPA)와 무관한 불변 표현. 아웃바운드 포트([github.lms.lemuel.xr.safety.application.port.out.SafetyAlertPort])는
 * 이 타입만 주고받으며, `SafetyAlertJpaEntity` 는
 * `SafetyAlertPersistenceAdapter` 안에만 갇혀 있다.
 *
 * [id] 는 저장 전에는 null, 저장 후 어댑터가 채운 값으로 반환된다.
 */
data class SafetyAlert(
    val id: Long?,
    val userId: UUID?,
    val appSessionId: UUID?,
    val emotionLogId: Long?,
    val matchedPattern: String,
    val severity: String,
    val triggerSource: String,
    val rawExcerptHash: String?,
    val shownResources: List<Map<String, Any?>>?,
    val userAcknowledged: Boolean,
    val createdAt: LocalDateTime?,
)
