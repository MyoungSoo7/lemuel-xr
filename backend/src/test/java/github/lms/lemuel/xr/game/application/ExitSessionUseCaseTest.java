package github.lms.lemuel.xr.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExitSessionUseCaseTest {

    @Mock GameSessionRepository sessions;
    @InjectMocks ExitSessionUseCase uc;

    private GameSessionJpaEntity live(UUID id) {
        GameSessionJpaEntity e = new GameSessionJpaEntity();
        e.setId(id);
        e.setStartedAt(LocalDateTime.now());
        return e;
    }

    @Test
    void execute_reason_atScene_지정() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid);
        when(sessions.findById(sid)).thenReturn(Optional.of(e));

        var r = uc.execute(sid, "panic", 3);

        assertThat(r.exitedAt()).isNotNull();
        assertThat(e.getAbandonedAt()).isNotNull();
        assertThat(e.getFinalOutcome()).isEqualTo("safe_exit:panic");
        assertThat(e.getSceneCountCompleted()).isEqualTo((short) 3);
    }

    @Test
    void execute_reason_null_이면_user_choice_default() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid);
        when(sessions.findById(sid)).thenReturn(Optional.of(e));

        uc.execute(sid, null, null);

        assertThat(e.getFinalOutcome()).isEqualTo("safe_exit:user_choice");
    }

    @Test
    void execute_세션없음_E_SESSION_NOT_FOUND() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> uc.execute(sid, "x", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND);
    }

    @Test
    void execute_이미완료_E_SESSION_INVALID() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid);
        e.setCompletedAt(LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> uc.execute(sid, "x", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID);
    }

    @Test
    void execute_이미abandoned_E_SESSION_INVALID() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid);
        e.setAbandonedAt(LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> uc.execute(sid, "x", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID);
    }
}
