package github.lms.lemuel.xr.safety.application.port.out

import github.lms.lemuel.xr.safety.domain.CrisisResource

/**
 * CrisisResource 영속성 아웃바운드 포트.
 *
 * Interface Segregation — 앱에서 실제 호출하는 조회 메서드만 선언한다 (full CRUD 슈퍼셋 아님).
 * 도메인 타입([CrisisResource])만 주고받는다. `*JpaEntity` 는 어댑터 안에만 존재.
 */
interface CrisisResourcePort {

    fun findByRegionAndLocaleAndActiveOrderByPriority(
        region: String,
        locale: String,
        active: Boolean,
    ): List<CrisisResource>

    fun findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc(
        region: String,
        locale: String,
    ): List<CrisisResource>
}
