package github.lms.lemuel.xr.outbox.application.port.out

import github.lms.lemuel.xr.outbox.domain.OutboxEvent
import org.springframework.data.domain.Pageable

/**
 * outbox_events 영속 포트 (ISP) — application 이 실제로 쓰는 메서드만 노출.
 *
 * - [github.lms.lemuel.xr.outbox.application.PublishEventUseCase] 의 pending INSERT ([save])
 * - [github.lms.lemuel.xr.outbox.application.OutboxRelayJob] 의 status 배치 폴링 ([findByStatus])
 *
 * 포트는 순수 도메인 [OutboxEvent] 만 오간다 — Hibernate `*JpaEntity` 를 노출하지 않는다.
 * PENDING→PUBLISHED(sent) 상태머신·event_id 시맨틱은 도메인 모델이 그대로 보존한다.
 */
interface OutboxEventPort {

    /** 호출자 트랜잭션 안에서 outbox_events 에 이벤트를 INSERT/UPDATE 한다. */
    fun save(event: OutboxEvent): OutboxEvent

    /** 주어진 status 의 이벤트를 createdAt 오름차순으로 배치 조회한다. */
    fun findByStatus(status: String, page: Pageable): List<OutboxEvent>
}
