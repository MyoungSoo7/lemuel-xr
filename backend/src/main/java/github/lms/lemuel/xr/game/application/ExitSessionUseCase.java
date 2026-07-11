package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 emergency exit — final_outcome='safe_exit' + abandoned_at.
 * SafetyController 의 /exit endpoint 에서 호출.
 */
@Service
@RequiredArgsConstructor
public class ExitSessionUseCase {

    private final GameSessionRepository sessions;

    @Transactional
    public Result execute(UUID sessionId, String reason, Integer atScene) {
        GameSessionJpaEntity e = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.E_SESSION_NOT_FOUND));
        if (e.getCompletedAt() != null || e.getAbandonedAt() != null) {
            throw new AppException(ErrorCode.E_SESSION_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        e.setAbandonedAt(now);
        e.setFinalOutcome("safe_exit:" + (reason == null ? "user_choice" : reason));
        if (atScene != null) e.setSceneCountCompleted(atScene.shortValue());
        return new Result(e.getId(), now);
    }

    public record Result(UUID sessionId, LocalDateTime exitedAt) {}
}
