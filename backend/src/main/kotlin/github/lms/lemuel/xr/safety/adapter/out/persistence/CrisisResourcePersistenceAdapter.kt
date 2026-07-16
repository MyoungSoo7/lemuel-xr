package github.lms.lemuel.xr.safety.adapter.out.persistence

import github.lms.lemuel.xr.safety.application.port.out.CrisisResourcePort
import github.lms.lemuel.xr.safety.domain.CrisisResource
import org.springframework.stereotype.Component

/**
 * [CrisisResourcePort] 구현 — Spring Data [CrisisResourceJpaRepository] 위임.
 *
 * [CrisisResourceJpaEntity] ↔ [CrisisResource] 매핑을 담당하는 유일한 지점.
 * JPA 엔티티는 이 어댑터 밖으로 나가지 않는다.
 */
@Component
class CrisisResourcePersistenceAdapter(
    private val repository: CrisisResourceJpaRepository,
) : CrisisResourcePort {

    override fun findByRegionAndLocaleAndActiveOrderByPriority(
        region: String,
        locale: String,
        active: Boolean,
    ): List<CrisisResource> =
        repository.findByRegionAndLocaleAndActiveOrderByPriority(region, locale, active)
            .map(::toDomain)

    override fun findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc(
        region: String,
        locale: String,
    ): List<CrisisResource> =
        repository.findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc(region, locale)
            .map(::toDomain)

    private fun toDomain(e: CrisisResourceJpaEntity): CrisisResource =
        CrisisResource(
            id = e.id,
            region = e.region!!,
            locale = e.locale!!,
            name = e.name!!,
            contactType = e.contactType!!,
            contactValue = e.contactValue!!,
            description = e.description,
            hours = e.hours,
            category = e.category,
            priority = e.priority,
            active = e.active == true,
            createdAt = e.createdAt,
        )
}
