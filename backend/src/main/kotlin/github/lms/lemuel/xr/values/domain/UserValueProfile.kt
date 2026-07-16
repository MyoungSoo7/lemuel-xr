package github.lms.lemuel.xr.values.domain

import java.time.OffsetDateTime
import java.util.UUID

/**
 * 사용자별 7 가치 프로파일 — 불변 도메인 모델.
 *
 * Hibernate `UserValueProfileJpaEntity` 로부터 격리된 순수 도메인 타입.
 * 영속성 어댑터에서만 엔티티 ↔ 이 data class 로 매핑한다.
 *
 * [valuesJson] 키: "1"~"7" 문자열.
 * value: {title, anchor_character?, anchor_scripture?, note?}.
 *
 * @property id            프로파일 id (UUID)
 * @property userId        사용자 id
 * @property valuesJson    7 가치 JSON (never null — 빈 맵으로 정규화)
 * @property startedAt     프로파일 생성 시각
 * @property lastUpdatedAt 마지막 갱신 시각
 */
data class UserValueProfile(
    val id: UUID,
    val userId: UUID,
    val valuesJson: Map<String, Any?>,
    val startedAt: OffsetDateTime,
    val lastUpdatedAt: OffsetDateTime,
) {
    companion object {
        /** null valuesJson 을 빈 맵으로 정규화하는 팩토리. */
        fun of(
            id: UUID,
            userId: UUID,
            valuesJson: Map<String, Any?>?,
            startedAt: OffsetDateTime,
            lastUpdatedAt: OffsetDateTime,
        ): UserValueProfile =
            UserValueProfile(id, userId, valuesJson ?: emptyMap(), startedAt, lastUpdatedAt)
    }
}
