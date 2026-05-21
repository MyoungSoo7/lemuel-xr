package github.lms.lemuel.xr.game.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.game.application.CompleteGameSessionUseCase;
import github.lms.lemuel.xr.game.application.DecideSceneUseCase;
import github.lms.lemuel.xr.game.application.StartGameSessionUseCase;
import github.lms.lemuel.xr.game.domain.Character;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/game/{character}/* — 4 인물 generic. character ∈ {joseph, moses, david, jesus}.
 *
 * <p>기존 /api/game/joseph/* 는 JosephGameController 에 alias 로 유지 (deprecated).</p>
 */
@RestController
@RequestMapping("/api/game/{character}")
@RequiredArgsConstructor
public class GameController {

    private final StartGameSessionUseCase startUc;
    private final DecideSceneUseCase decideUc;
    private final CompleteGameSessionUseCase completeUc;

    @PostMapping("/start")
    public ResponseEntity<StartResponse> start(@PathVariable String character,
                                                @RequestBody StartRequest req) {
        var c = Character.from(character);
        var r = startUc.execute(
                RequestContext.currentUserId(),
                c,
                new StartGameSessionUseCase.Input(
                        req.mode(),
                        req.client() == null ? RequestContext.currentDeviceType() : req.client().deviceType(),
                        req.client() == null ? null : req.client().capabilities(),
                        req.linkedEmotionLogId())
        );
        return ResponseEntity.ok(new StartResponse(
                r.sessionId(), c.dbValue(), r.currentScene(), r.totalScenes(),
                r.appliedMode(), r.scenePayload()));
    }

    @PostMapping("/{sid}/decide")
    public ResponseEntity<DecideResponse> decide(@PathVariable String character,
                                                  @PathVariable("sid") UUID sid,
                                                  @RequestBody DecideRequest req) {
        var c = Character.from(character);
        var r = decideUc.execute(sid, c, new DecideSceneUseCase.Input(
                req.sceneId(), req.decision(), req.interactionMeta(), req.mode()
        ));
        return ResponseEntity.ok(new DecideResponse(
                r.sessionId(), r.previousScene(), r.currentScene(), r.scenePayload()));
    }

    @PostMapping("/{sid}/complete")
    public ResponseEntity<CompleteResponse> complete(@PathVariable String character,
                                                      @PathVariable("sid") UUID sid,
                                                      @RequestBody CompleteRequest req) {
        Character.from(character);  // 검증만
        var r = completeUc.execute(sid, req.finalOutcome(), req.closingMessage());
        return ResponseEntity.ok(new CompleteResponse(
                r.sessionId(), r.completedAt(), r.durationSeconds()));
    }

    // --- DTOs ---

    public record StartRequest(
            String mode,
            ClientCapabilities client,
            Long linkedEmotionLogId
    ) {}

    public record ClientCapabilities(
            String deviceType,
            Map<String, Object> capabilities
    ) {}

    public record StartResponse(
            UUID sessionId, String character,
            int currentScene, int totalScenes,
            String appliedMode, Map<String, Object> scenePayload
    ) {}

    public record DecideRequest(
            int sceneId,
            Map<String, Object> decision,
            Map<String, Object> interactionMeta,
            String mode
    ) {}

    public record DecideResponse(
            UUID sessionId, int previousScene, int currentScene, Map<String, Object> scenePayload
    ) {}

    public record CompleteRequest(String finalOutcome, String closingMessage) {}

    public record CompleteResponse(UUID sessionId, LocalDateTime completedAt, Integer durationSeconds) {}
}
