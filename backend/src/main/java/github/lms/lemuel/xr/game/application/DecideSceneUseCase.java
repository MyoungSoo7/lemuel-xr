package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameDecisionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameDecisionRepository;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DecideSceneUseCase {

    private final GameSessionRepository sessions;
    private final GameDecisionRepository decisions;
    private final ScenarioYamlLoader loader;

    @Transactional
    public Result execute(UUID sessionId, Character character, Input input) {
        GameSessionJpaEntity e = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.E_SESSION_NOT_FOUND));
        if (e.getCompletedAt() != null || e.getAbandonedAt() != null) {
            throw new AppException(ErrorCode.E_SESSION_INVALID);
        }
        if (!e.getCharacter().equalsIgnoreCase(character.dbValue())) {
            throw new AppException(ErrorCode.E_CHARACTER_UNKNOWN);
        }
        if (input.mode() != null && e.getChosenDimension() != null
                && !input.mode().equalsIgnoreCase(e.getChosenDimension())) {
            throw new AppException(ErrorCode.E_MODE_MISMATCH);
        }

        Scenario scenario = loader.forCharacter(character);
        Scenario.Scene currentScene = scenario.scene(input.sceneId());

        // 결정 영속
        GameDecisionJpaEntity d = new GameDecisionJpaEntity();
        d.setGameSessionId(sessionId);
        d.setSceneNumber((short) input.sceneId());
        d.setSceneName(currentScene.title());
        d.setDecision(input.decision());
        d.setInteractionMeta(input.interactionMeta());
        d.setDecidedAt(LocalDateTime.now());
        decisions.save(d);

        // 세션 decisions JSONB 업데이트
        e.getDecisions().put("scene" + input.sceneId(), input.decision());
        e.setSceneCountCompleted((short) Math.max(
                e.getSceneCountCompleted() == null ? 0 : e.getSceneCountCompleted(),
                input.sceneId()));

        Integer next = currentScene.next();
        Map<String, Object> nextPayload = next == null
                ? Map.of("type", "end")
                : StartGameSessionUseCase.buildScenePayload(scenario, next);

        // 실시간 LLM 응답은 ai/ 모듈에서 별도 호출 — 본 use case 는 페이로드만 반환.
        return new Result(sessionId, input.sceneId(),
                next == null ? input.sceneId() : next, nextPayload);
    }

    public record Input(
            int sceneId,
            Map<String, Object> decision,
            Map<String, Object> interactionMeta,
            String mode
    ) {}

    public record Result(UUID sessionId, int previousScene, int currentScene,
                          Map<String, Object> scenePayload) {}
}
