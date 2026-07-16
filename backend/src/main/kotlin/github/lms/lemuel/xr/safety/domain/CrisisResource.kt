package github.lms.lemuel.xr.safety.domain

import java.time.LocalDateTime

/**
 * 위기 자원 도메인 모델 — region/locale 별 상담·긴급 연락 카탈로그 항목.
 *
 * 영속화 세부(JPA)와 무관한 불변 표현. 아웃바운드 포트([github.lms.lemuel.xr.safety.application.port.out.CrisisResourcePort])는
 * 이 타입만 주고받으며, `CrisisResourceJpaEntity` 는
 * `CrisisResourcePersistenceAdapter` 안에만 갇혀 있다.
 */
data class CrisisResource(
    val id: Long?,
    val region: String,
    val locale: String,
    val name: String,
    val contactType: String,
    val contactValue: String,
    val description: String?,
    val hours: String?,
    val category: String?,
    val priority: Short?,
    val active: Boolean,
    val createdAt: LocalDateTime?,
)
