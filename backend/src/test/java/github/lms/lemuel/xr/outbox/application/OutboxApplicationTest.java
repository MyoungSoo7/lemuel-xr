package github.lms.lemuel.xr.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.outbox.adapter.out.persistence.OutboxEventJpaEntity;
import github.lms.lemuel.xr.outbox.adapter.out.persistence.OutboxEventRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * outbox/application 단위 테스트 — PublishEventUseCase INSERT + OutboxRelayJob pending→sent 배치.
 */
@ExtendWith(MockitoExtension.class)
class OutboxApplicationTest {

    @Mock
    OutboxEventRepository repo;

    // ─────────────────────────── PublishEventUseCase ───────────────────────────

    @Test
    void publish_pending_이벤트_INSERT() {
        PublishEventUseCase useCase = new PublishEventUseCase(repo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = Map.of("k", "v", "n", 42);
        UUID id = useCase.execute("session", "sess-1", "session.completed", payload);

        assertThat(id).isNotNull();
        ArgumentCaptor<OutboxEventJpaEntity> cap = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repo).save(cap.capture());
        OutboxEventJpaEntity saved = cap.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getAggregateType()).isEqualTo("session");
        assertThat(saved.getAggregateId()).isEqualTo("sess-1");
        assertThat(saved.getEventType()).isEqualTo("session.completed");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ─────────────────────────────── OutboxRelayJob ───────────────────────────────

    @Test
    void relay_빈배치면_아무것도_저장안함() {
        OutboxRelayJob job = new OutboxRelayJob(repo);
        when(repo.findByStatus(eq("pending"), any(Pageable.class))).thenReturn(List.of());

        job.relay();

        // early return — entity 변경 없음.
        verify(repo).findByStatus(eq("pending"), any(Pageable.class));
    }

    @Test
    void relay_pending_이벤트_sent_마킹() {
        OutboxRelayJob job = new OutboxRelayJob(repo);
        OutboxEventJpaEntity e1 = newPending();
        OutboxEventJpaEntity e2 = newPending();
        when(repo.findByStatus(eq("pending"), any(Pageable.class))).thenReturn(List.of(e1, e2));

        job.relay();

        assertThat(e1.getStatus()).isEqualTo("sent");
        assertThat(e1.getSentAt()).isNotNull();
        assertThat(e2.getStatus()).isEqualTo("sent");
        assertThat(e2.getSentAt()).isNotNull();
    }

    @Test
    void relay_예외발생시_failed_마킹_attempt_증가() {
        OutboxRelayJob job = new OutboxRelayJob(repo);
        // setSentAt 이 던지도록 서브클래스로 예외 유발.
        OutboxEventJpaEntity boom = new OutboxEventJpaEntity() {
            @Override
            public void setSentAt(java.time.LocalDateTime t) {
                throw new RuntimeException("boom");
            }
        };
        boom.setId(UUID.randomUUID());
        boom.setStatus("pending");
        boom.setAttemptCount((short) 0);
        when(repo.findByStatus(eq("pending"), any(Pageable.class))).thenReturn(List.of(boom));

        job.relay();

        assertThat(boom.getStatus()).isEqualTo("failed");
        assertThat(boom.getAttemptCount()).isEqualTo((short) 1);
        assertThat(boom.getLastError()).isEqualTo("boom");
        verify(repo, never()).save(any());
    }

    private OutboxEventJpaEntity newPending() {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setStatus("pending");
        e.setAttemptCount((short) 0);
        return e;
    }
}
