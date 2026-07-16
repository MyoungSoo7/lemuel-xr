package github.lms.lemuel.xr.values.application.port.out

import github.lms.lemuel.xr.values.domain.UserValuePractice
import java.time.OffsetDateTime
import java.util.UUID

/**
 * UserValuePractice 영속성 아웃바운드 포트.
 *
 * Interface Segregation — values 컨텍스트에서 실제 호출하는 메서드만 선언한다.
 *
 * 순수 도메인 타입 [UserValuePractice] 로 주고받는다 — Hibernate 엔티티는 어댑터 안에만 머문다.
 * journey 컨텍스트도 이 포트를 통해 [UserValuePractice] 목록을 받는다.
 */
interface UserValuePracticePort {

    fun findRecent(userId: UUID, since: OffsetDateTime): List<UserValuePractice>

    fun save(practice: UserValuePractice): UserValuePractice
}
