package github.lms.lemuel.xr.game.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.game.application.CompleteGameSessionUseCase;
import github.lms.lemuel.xr.game.application.DecideSceneUseCase;
import github.lms.lemuel.xr.game.application.StartGameSessionUseCase;
import github.lms.lemuel.xr.game.domain.Character;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry meter;

    @PostMapping("/start")
    @Timed(value = "game.start", extraTags = {"endpoint", "start"}, percentiles = {0.5, 0.95, 0.99})
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
        Counter.builder("game.session.started")
                .tag("character", c.dbValue())
                .tag("mode", req.mode() == null ? "unspecified" : req.mode())
                .register(meter).increment();
        return ResponseEntity.ok(new StartResponse(
                r.sessionId(), c.dbValue(), r.currentScene(), r.totalScenes(),
                r.appliedMode(), r.scenePayload()));
    }

    @PostMapping("/{sid}/decide")
    @Timed(value = "game.decide", percentiles = {0.5, 0.95, 0.99},
           description = "Scene 결정 처리 latency — LLM 실시간 호출 시 길어짐")
    public ResponseEntity<DecideResponse> decide(@PathVariable String character,
                                                  @PathVariable("sid") UUID sid,
                                                  @RequestBody DecideRequest req) {
        var c = Character.from(character);
        var r = decideUc.execute(sid, c, new DecideSceneUseCase.Input(
                req.sceneId(), req.decision(), req.interactionMeta(), req.mode()
        ));
        return ResponseEntity.ok(new DecideResponse(
                r.sessionId(), r.previousScene(), r.currentScene(),
                r.scenePayload(), r.responseText()));
    }

    @PostMapping("/{sid}/complete")
    public ResponseEntity<CompleteResponse> complete(@PathVariable String character,
                                                      @PathVariable("sid") UUID sid,
                                                      @RequestBody CompleteRequest req) {
        var c = Character.from(character);  // 검증
        var r = completeUc.execute(sid, req.finalOutcome(), req.closingMessage());
        Counter.builder("game.session.completed")
                .tag("character", c.dbValue())
                .register(meter).increment();
        return ResponseEntity.ok(new CompleteResponse(
                r.sessionId(), r.completedAt(), r.durationSeconds(),
                r.valuePrompt()));
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
            UUID sessionId, int previousScene, int currentScene,
            Map<String, Object> scenePayload,
            /** Phase 2-A — 직전 결정에 대한 정적 모놀로그/아웃컴 텍스트 (yml lookup). null 가능. */
            String responseText
    ) {}

    public record CompleteRequest(String finalOutcome, String closingMessage) {}

    public record CompleteResponse(
            UUID sessionId, LocalDateTime completedAt, Integer durationSeconds,
            /** VR→AR 연계: 이 인물이 빛내는 AR 가치 1~7 + 실천 prompt. null 가능. */
            github.lms.lemuel.xr.game.application.CompleteGameSessionUseCase.ValuePrompt valuePrompt
    ) {}
}
