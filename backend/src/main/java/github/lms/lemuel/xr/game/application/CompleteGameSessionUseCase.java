package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteGameSessionUseCase {

    private final GameSessionRepository sessions;

    @Transactional
    public Result execute(UUID sessionId, String finalOutcome, String closingMessage) {
        GameSessionJpaEntity e = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.E_SESSION_NOT_FOUND));
        if (e.getCompletedAt() != null || e.getAbandonedAt() != null) {
            throw new AppException(ErrorCode.E_SESSION_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        e.setCompletedAt(now);
        e.setFinalOutcome(finalOutcome);
        e.setClosingMessage(closingMessage);
        if (e.getStartedAt() != null) {
            e.setDurationSeconds((int) Duration.between(e.getStartedAt(), now).getSeconds());
        }
        return new Result(e.getId(), now, e.getDurationSeconds());
    }

    public record Result(UUID sessionId, LocalDateTime completedAt, Integer durationSeconds) {}
}
