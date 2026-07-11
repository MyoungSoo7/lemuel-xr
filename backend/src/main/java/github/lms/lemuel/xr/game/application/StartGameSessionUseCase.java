package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StartGameSessionUseCase {

    private final GameSessionRepository sessions;
    private final ScenarioYamlLoader loader;

    @Transactional
    public Result execute(UUID userId, Character character, Input input) {
        Scenario scenario = loader.forCharacter(character);

        GameSessionJpaEntity e = new GameSessionJpaEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setCharacter(character.dbValue());
        e.setChosenDimension(input.mode());
        e.setStartedAt(LocalDateTime.now());
        e.setDeviceType(input.deviceType());
        e.setCapabilities(input.capabilities());
        e.setTriggeredByEmotionLogId(input.linkedEmotionLogId());
        sessions.save(e);

        Map<String, Object> payload = buildScenePayload(scenario, 1);
        return new Result(e.getId(), 1, scenario.totalScenes(), input.mode(), payload);
    }

    static Map<String, Object> buildScenePayload(Scenario scenario, int sceneId) {
        Scenario.Scene sc = scenario.scene(sceneId);
        Map<String, Object> p = new HashMap<>();
        p.put("sceneId", sc.id());
        p.put("title", sc.title());
        p.put("type", sc.type());
        if (sc.interaction() != null) p.put("interaction", sc.interaction());
        if (sc.durationSec() != null) p.put("durationSec", sc.durationSec());
        if (sc.narrationId() != null) p.put("narrationId", sc.narrationId());
        if (sc.scriptureRef() != null) p.put("scriptureRef", sc.scriptureRef());
        if (Boolean.TRUE.equals(sc.realtimeLlm())) p.put("realtimeLlm", true);
        p.put("next", sc.next());
        if (sc.extras() != null) p.putAll(sc.extras());
        return p;
    }

    public record Input(
            String mode,
            String deviceType,
            Map<String, Object> capabilities,
            Long linkedEmotionLogId
    ) {}

    public record Result(UUID sessionId, int currentScene, int totalScenes,
                          String appliedMode, Map<String, Object> scenePayload) {}
}
