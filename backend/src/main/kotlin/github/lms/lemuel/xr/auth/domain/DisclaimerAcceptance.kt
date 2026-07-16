package github.lms.lemuel.xr.auth.domain

import java.time.OffsetDateTime
import java.util.UUID

/**
 * DisclaimerAcceptance 애그리거트 — 순수 도메인 모델 (Hibernate 비의존).
 *
 * 동의 audit row. [id] 는 DB IDENTITY 이므로 신규 생성 시 `null`.
 * [DisclaimerAcceptancePort] 가 주고받는 타입이며 `DisclaimerAcceptanceJpaEntity` 는
 * `DisclaimerAcceptancePersistenceAdapter` 안에서만 매핑된다.
 *
 * `@JvmRecord` — 아직 Java 인 호출부(테스트 등)가 record accessor 형태로 접근하므로
 * JVM record 로 컴파일해 accessor 호환을 유지한다.
 */
@JvmRecord
data class DisclaimerAcceptance(
    val id: Long?,
    val userId: UUID?,
    val acceptedAt: OffsetDateTime?,
    val disclaimerVersion: String?,
    val userAgent: String?,
    val ipHash: String?,
)
