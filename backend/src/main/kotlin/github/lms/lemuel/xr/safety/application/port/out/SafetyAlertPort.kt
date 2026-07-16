package github.lms.lemuel.xr.safety.application.port.out

import github.lms.lemuel.xr.safety.domain.SafetyAlert

/**
 * SafetyAlert 영속성 아웃바운드 포트.
 *
 * Interface Segregation — 앱에서 실제 호출하는 메서드만 선언한다 (full CRUD 슈퍼셋 아님).
 * 현재 앱 경로는 alert 저장만 수행한다.
 * 도메인 타입([SafetyAlert])만 주고받는다. `*JpaEntity` 는 어댑터 안에만 존재.
 * 저장 후 id 가 채워진 도메인 모델을 반환한다.
 */
interface SafetyAlertPort {

    fun save(alert: SafetyAlert): SafetyAlert
}
