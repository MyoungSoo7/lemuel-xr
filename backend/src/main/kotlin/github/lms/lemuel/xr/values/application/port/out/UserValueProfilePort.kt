package github.lms.lemuel.xr.values.application.port.out

import github.lms.lemuel.xr.values.domain.UserValueProfile
import java.util.Optional
import java.util.UUID

/**
 * UserValueProfile 영속성 아웃바운드 포트.
 *
 * Interface Segregation — 앱에서 실제 호출하는 메서드만 선언한다 (full CRUD 슈퍼셋 아님).
 *
 * 순수 도메인 타입 [UserValueProfile] 로 주고받는다 — Hibernate 엔티티는 어댑터 안에만 머문다.
 */
interface UserValueProfilePort {

    fun findByUserId(userId: UUID): Optional<UserValueProfile>

    fun save(profile: UserValueProfile): UserValueProfile
}
