package github.lms.lemuel.xr.outbox.application;

import github.lms.lemuel.xr.outbox.adapter.out.persistence.OutboxEventJpaEntity;
import github.lms.lemuel.xr.outbox.adapter.out.persistence.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 트랜잭션 일관성 보장된 이벤트 발행 — 호출자 transaction 안에 INSERT. */
@Service
@RequiredArgsConstructor
public class PublishEventUseCase {

    private final OutboxEventRepository repo;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID execute(String aggregateType, String aggregateId,
                        String eventType, Map<String, Object> payload) {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        e.setPayload(payload);
        e.setStatus("pending");
        e.setCreatedAt(LocalDateTime.now());
        repo.save(e);
        return e.getId();
    }
}
