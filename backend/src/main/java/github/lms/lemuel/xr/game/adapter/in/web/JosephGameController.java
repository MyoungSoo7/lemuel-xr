package github.lms.lemuel.xr.game.adapter.in.web;

import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 요셉 미션 게임 API.
 *
 * <ul>
 *   <li>POST /api/game/joseph/start              — 세션 생성, Scene 1 페이로드 반환</li>
 *   <li>POST /api/game/joseph/{sid}/decide       — Scene n 의 사용자 선택 기록, 다음 scene 반환</li>
 *   <li>POST /api/game/joseph/{sid}/complete     — 세션 종료 + 최종 결과 outcome 저장</li>
 * </ul>
 *
 * <p>MVP 단계는 시나리오 데이터 + 사전 캐시 응답을 그대로 반환. Scene 4 의 실시간 LLM 호출은
 * 별도 LlmCacheService 에 위임 (다음 PR).</p>
 */
@RestController
@RequestMapping("/api/game/joseph")
@RequiredArgsConstructor
public class JosephGameController {

    private final GameSessionRepository repo;

    record StartRequest(UUID userId, String deviceType) {}

    record SessionResponse(UUID sessionId, int currentScene, Map<String, Object> scenePayload) {}

    @PostMapping("/start")
    public ResponseEntity<SessionResponse> start(@RequestBody StartRequest req) {
        UUID uid = req.userId() == null ? UUID.randomUUID() : req.userId();
        GameSessionJpaEntity e = new GameSessionJpaEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(uid);
        e.setCharacter("joseph");
        e.setStartedAt(LocalDateTime.now());
        repo.save(e);
        return ResponseEntity.ok(new SessionResponse(e.getId(), 1, scenePayload(1, null)));
    }

    record DecideRequest(int sceneId, Object decision) {}

    @PostMapping("/{sid}/decide")
    public ResponseEntity<SessionResponse> decide(@PathVariable("sid") UUID sid,
                                                  @RequestBody DecideRequest req) {
        GameSessionJpaEntity e = repo.findById(sid).orElseThrow();
        e.getDecisions().put("scene" + req.sceneId(), req.decision());
        repo.save(e);
        int next = req.sceneId() + 1;
        return ResponseEntity.ok(new SessionResponse(sid, next, scenePayload(next, e)));
    }

    record CompleteRequest(String finalOutcome) {}

    @PostMapping("/{sid}/complete")
    public ResponseEntity<Void> complete(@PathVariable("sid") UUID sid,
                                         @RequestBody CompleteRequest req) {
        GameSessionJpaEntity e = repo.findById(sid).orElseThrow();
        e.setFinalOutcome(req.finalOutcome());
        e.setCompletedAt(LocalDateTime.now());
        repo.save(e);
        return ResponseEntity.noContent().build();
    }

    /**
     * MVP 정적 응답. 실제로는 scenarios/joseph.yml 을 로드해 매핑하는 ScenarioLoader 가 필요.
     * Day 4 에서 yaml 기반 동적 로딩으로 교체 예정.
     */
    private Map<String, Object> scenePayload(int sceneId, GameSessionJpaEntity session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sceneId", sceneId);
        switch (sceneId) {
            case 1 -> {
                payload.put("title", "파라오의 꿈");
                payload.put("type", "cinematic");
                payload.put("narrationId", "joseph.scene1.narration");
                payload.put("durationSec", 60);
            }
            case 2 -> {
                payload.put("title", "풍년 — 저장 결정");
                payload.put("type", "pick_one");
                payload.put("options", new Object[]{
                        Map.of("id", "save_20", "label", "1/5 저장"),
                        Map.of("id", "save_33", "label", "1/3 저장"),
                        Map.of("id", "save_50", "label", "1/2 저장")
                });
            }
            case 3 -> {
                payload.put("title", "흉년 — 분배 결정");
                payload.put("type", "distribute");
                payload.put("queues", new Object[]{
                        Map.of("id", "farmer",    "label", "이집트 농민"),
                        Map.of("id", "immigrant", "label", "이주민 가족"),
                        Map.of("id", "merchant",  "label", "무역 상인")
                });
            }
            case 4 -> {
                payload.put("title", "형제와 재회");
                payload.put("type", "pick_one");
                payload.put("options", new Object[]{
                        Map.of("id", "reveal", "label", "정체를 즉시 밝힌다"),
                        Map.of("id", "test",   "label", "잠시 시험한다"),
                        Map.of("id", "silent", "label", "침묵한다")
                });
                payload.put("realtimeLlm", true);
            }
            case 5 -> {
                payload.put("title", "결말");
                payload.put("type", "outro");
                payload.put("scriptureRef", "gen-45:5");
            }
            default -> payload.put("type", "end");
        }
        return payload;
    }
}
