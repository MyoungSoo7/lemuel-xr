package github.lms.lemuel.xr.auth.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

/**
 * User 애그리거트 — 순수 도메인 모델 (Hibernate 비의존).
 *
 * 불변 data class. 아웃바운드 포트([UserPort])가 주고받는 타입이며,
 * `UserJpaEntity` 는 `UserPersistenceAdapter` 안에서만 매핑된다.
 *
 * faith_tone / preferred_mode / haptic_intensity 는 DB 에 raw string 으로 저장되고
 * 컨트롤러가 그대로 JSON 으로 내보내므로, 라운드트립·거동 보존을 위해 String 으로 유지한다.
 * 도메인 enum([FaithTone]/[Dimension]/[HapticIntensity])은 해석이 필요한
 * 호출부에서 `from(...)` 으로 변환해 쓸 수 있다.
 *
 * disclaimer 동의 여부 접근자는 [disclaimerAcceptedAt] (property). null 이면 미동의.
 *
 * `@JvmRecord` — 아직 Java 인 `common/security/DisclaimerGateFilter` 가 record accessor
 * `disclaimerAcceptedAt()` 로 읽으므로, JVM record 로 컴파일해 Java 측 accessor 호환을 유지한다.
 * Kotlin 측에서는 그대로 property 접근(`u.disclaimerAcceptedAt`)이 동작한다.
 */
@JvmRecord
data class User(
    val id: UUID?,
    val createdAt: LocalDateTime?,          // users.created_at (V1 TIMESTAMP)
    val updatedAt: OffsetDateTime?,         // users.updated_at (V3 TIMESTAMPTZ)
    val userType: String?,
    val externalId: String?,
    val faithTone: String?,
    val preferredMode: String?,
    val hapticIntensity: String?,
    val skipIntroSilence: Boolean?,
    val dataRetentionDays: Int?,
    val deletedAt: OffsetDateTime?,
    val disclaimerAcceptedAt: OffsetDateTime?,
    val disclaimerVersion: String?,
    val aiOptOut: Boolean?,
)
