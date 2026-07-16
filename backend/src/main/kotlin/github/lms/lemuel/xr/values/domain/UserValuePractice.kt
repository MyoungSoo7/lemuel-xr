package github.lms.lemuel.xr.values.domain

import java.time.OffsetDateTime
import java.util.UUID

/**
 * 일별 실천 로그 1건 — 불변 도메인 모델. CDR Index 계산의 원천 데이터.
 *
 * Hibernate `UserValuePracticeJpaEntity` 로부터 격리된 순수 도메인 타입.
 * 영속성 어댑터에서만 엔티티 ↔ 이 data class 로 매핑한다.
 *
 * Cross-context: journey 컨텍스트가 `UserValuePracticePort.findRecent(...)` 로
 * 이 타입 목록을 받아 [practicedAt] 를 읽는다.
 *
 * @property id                실천 로그 id (신규 미저장 시 null)
 * @property userId            사용자 id
 * @property valueId           가치 id (1..7)
 * @property practicedAt       실천 시각
 * @property durationSec       실천 시간(초, nullable)
 * @property note              메모 (nullable)
 * @property linkedCharacter   연계 인물 (nullable)
 * @property linkedGameSession 연계 게임 세션 (nullable)
 */
data class UserValuePractice(
    val id: Long?,
    val userId: UUID,
    val valueId: Short,
    val practicedAt: OffsetDateTime,
    val durationSec: Int?,
    val note: String?,
    val linkedCharacter: String?,
    val linkedGameSession: UUID?,
)
