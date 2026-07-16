package github.lms.lemuel.xr.recovery.application.port.out

import github.lms.lemuel.xr.recovery.domain.RecoveryMetric
import java.time.LocalDate
import java.util.UUID

/**
 * recovery_metrics 영속성 아웃바운드 포트.
 *
 * Interface Segregation — 앱에서 실제 호출하는 메서드만 선언한다 (full CRUD 슈퍼셋 아님).
 * 조회는 [findRecent], 일별 집계 잡의 쓰기는 [save].
 *
 * 포트는 순수 도메인 타입 [RecoveryMetric] 로만 대화한다 — JPA 엔티티는 어댑터 내부 상세.
 */
interface RecoveryMetricPort {

    fun findRecent(userId: UUID, since: LocalDate): List<RecoveryMetric>

    fun save(metric: RecoveryMetric): RecoveryMetric
}
