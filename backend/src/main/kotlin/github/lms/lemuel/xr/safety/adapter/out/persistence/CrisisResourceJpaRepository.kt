package github.lms.lemuel.xr.safety.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CrisisResourceJpaRepository : JpaRepository<CrisisResourceJpaEntity, Long> {

    fun findByRegionAndLocaleAndActiveOrderByPriority(
        region: String,
        locale: String,
        active: Boolean,
    ): List<CrisisResourceJpaEntity>

    fun findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc(
        region: String,
        locale: String,
    ): List<CrisisResourceJpaEntity>
}
