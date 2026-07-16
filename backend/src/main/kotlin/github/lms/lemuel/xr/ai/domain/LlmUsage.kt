package github.lms.lemuel.xr.ai.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * LLM 사용량(토큰·비용) 기록 1건 — 불변 도메인 모델.
 *
 * Hibernate `LlmUsageJpaEntity` 로부터 격리된 순수 도메인 타입.
 * 영속성 어댑터(`LlmUsagePersistenceAdapter`) 에서만 엔티티 ↔ 이 타입으로 매핑한다.
 *
 * @property id               사용량 id (신규 미저장 시 null)
 * @property occurredAt       발생 시각
 * @property userId           사용자 id (nullable)
 * @property purpose          용도
 * @property provider         LLM 제공자
 * @property model            모델명
 * @property promptTokens     프롬프트 토큰 수 (nullable)
 * @property completionTokens 완성 토큰 수 (nullable)
 * @property latencyMs        지연(ms, nullable)
 * @property cacheHit         캐시 히트 여부
 * @property costUsd          비용(USD, nullable)
 * @property requestId        요청 id (nullable)
 * @property success          성공 여부
 * @property errorCode        에러 코드 (nullable)
 */
data class LlmUsage(
    val id: Long?,
    val occurredAt: LocalDateTime,
    val userId: UUID?,
    val purpose: String,
    val provider: String,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val latencyMs: Int?,
    val cacheHit: Boolean,
    val costUsd: BigDecimal?,
    val requestId: String?,
    val success: Boolean,
    val errorCode: String?,
)
