package github.lms.lemuel.xr.auth.application.port.out

import github.lms.lemuel.xr.auth.domain.DisclaimerAcceptance

/**
 * DisclaimerAcceptance 영속성 아웃바운드 포트.
 *
 * 순수 도메인 [DisclaimerAcceptance] 만 주고받는다 (Hibernate `*JpaEntity` 노출 금지).
 * Interface Segregation — 앱에서 실제 호출하는 메서드만 선언한다.
 */
interface DisclaimerAcceptancePort {

    fun save(acceptance: DisclaimerAcceptance): DisclaimerAcceptance
}
