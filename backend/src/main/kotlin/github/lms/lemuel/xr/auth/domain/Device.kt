package github.lms.lemuel.xr.auth.domain

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Device 애그리거트 — 순수 도메인 모델 (Hibernate 비의존).
 *
 * 불변 data class. [DevicePort] 가 주고받는 타입. `DeviceJpaEntity` 는
 * `DevicePersistenceAdapter` 안에서만 매핑된다.
 *
 * `@JvmRecord` — 아직 Java 인 호출부(테스트 등)가 record accessor 형태로 접근하므로
 * JVM record 로 컴파일해 accessor 호환을 유지한다.
 */
@JvmRecord
data class Device(
    val id: UUID?,
    val userId: UUID?,
    val deviceType: String?,
    val deviceFingerprint: String?,
    val lastSeenAt: OffsetDateTime?,
    val createdAt: OffsetDateTime?,
)
