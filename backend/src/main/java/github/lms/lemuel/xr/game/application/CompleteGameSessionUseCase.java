package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 세션 완료. 시나리오의 마지막 scene 에서 *linked_values + value_prompt* 를 추출해
 * 응답에 포함 — 사용자가 *오늘의 AR 가치 실천 prompt* 를 즉시 받음.
 *
 * <p>CROSS-MAPPING-VR-AR.md §3 의 4×7 매핑이 시나리오 yml 의 outro scene 에 박혀있고,
 * 이 use case 가 *VR 세션 끝 → AR 일상 실천* 연결을 만든다.</p>
 */
@Service
@RequiredArgsConstructor
public class CompleteGameSessionUseCase {

    private final GameSessionRepository sessions;
    private final ScenarioYamlLoader scenarios;

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

        // 시나리오의 outro scene 에서 linked_values + value_prompt 추출.
        ValuePrompt vp = extractValuePrompt(e.getCharacter());

        return new Result(e.getId(), now, e.getDurationSeconds(), vp);
    }

    private ValuePrompt extractValuePrompt(String characterStr) {
        try {
            Character c = Character.from(characterStr);
            Scenario s = scenarios.forCharacter(c);
            // outro scene 찾기 (next == null 인 scene 또는 마지막)
            Scenario.Scene outro = s.scenes().stream()
                    .filter(sc -> sc.next() == null)
                    .findFirst()
                    .orElse(s.scenes().get(s.scenes().size() - 1));
            Map<String, Object> extras = outro.extras();
            if (extras == null) return null;
            Object linkedValues = extras.get("linked_values");
            Object prompt = extras.get("value_prompt");
            if (linkedValues == null && prompt == null) return null;
            List<Integer> values = linkedValues instanceof List<?> list
                    ? list.stream().filter(Integer.class::isInstance).map(Integer.class::cast).toList()
                    : List.of();
            return new ValuePrompt(values, prompt == null ? null : prompt.toString(), characterStr);
        } catch (Exception ex) {
            return null;
        }
    }

    /** VR 세션 완료 시 사용자가 받는 AR 가치 실천 prompt. */
    public record ValuePrompt(
            List<Integer> suggestedValueIds,
            String message,
            String linkedCharacter
    ) {}

    public record Result(UUID sessionId, LocalDateTime completedAt, Integer durationSeconds,
                          ValuePrompt valuePrompt) {}
}
