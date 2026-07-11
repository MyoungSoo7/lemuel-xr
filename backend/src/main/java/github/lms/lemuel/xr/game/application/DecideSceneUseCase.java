package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameDecisionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameDecisionRepository;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DecideSceneUseCase {

    private final GameSessionRepository sessions;
    private final GameDecisionRepository decisions;
    private final ScenarioYamlLoader loader;
    /** Phase 2-B — Scene.realtimeLlm=true 일 때 호출. 없으면 정적 fallback. */
    private final GenerateLlmResponseUseCase llm;

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

        // 직전 결정에 대한 응답 텍스트:
        //  · Scene.realtimeLlm=true → LLM 호출 (실패 시 정적 fallback)
        //  · 그 외 → yml 의 monologues/outcomes/reactions 에서 lookup
        String responseText = resolveResponseText(character, currentScene, input.decision(), e);

        return new Result(sessionId, input.sceneId(),
                next == null ? input.sceneId() : next, nextPayload, responseText);
    }

    /** Phase 2-B realtime LLM 우선, 실패/정적 Scene 은 yml fallback. */
    private String resolveResponseText(Character character, Scenario.Scene scene,
                                        Map<String, Object> decision, GameSessionJpaEntity session) {
        if (!Boolean.TRUE.equals(scene.realtimeLlm())) {
            return matchResponseText(scene, decision);
        }

        // promptKey: joseph.s4.reaction (Scene + decision key 는 variables 로)
        String decisionKey = extractDecisionKey(decision);
        String promptKey = String.format("%s.s%d.reaction", character.dbValue(), scene.id());

        Map<String, Object> vars = new HashMap<>();
        vars.put("character", character.dbValue());
        vars.put("scene", scene.id());
        vars.put("decision", decisionKey);
        // 이전 결정 컨텍스트 (Scene 3 분배 결과 등)
        if (session.getDecisions() != null) {
            vars.put("history", session.getDecisions());
        }

        try {
            var r = llm.execute("game_reaction", promptKey, vars);
            return r.text();
        } catch (Exception e) {
            // AI 사이드카 down / 인증 실패 / timeout — 정적 fallback 으로 graceful degradation
            log.warn("realtime LLM 실패 — 정적 fallback. character={} scene={} cause={}",
                    character, scene.id(), e.getMessage());
            return matchResponseText(scene, decision);
        }
    }

    /**
     * Scene 의 extras 에서 사용자 결정 키에 매칭되는 정적 텍스트를 찾는다.
     *
     * <p>지원 형식 — extras 의 {@code monologues} / {@code outcomes} / {@code reactions}
     * 중 하나가 Map&lt;String, String&gt; 이고, decision 의 key 또는 priority 값으로 lookup.
     *
     * <p>매칭 못 하면 null — Phase 2-B 의 LLM 호출 결과로 대체될 자리.
     */
    static String matchResponseText(Scenario.Scene scene, Map<String, Object> decision) {
        if (scene.extras() == null || decision == null) return null;

        // decision 에서 매칭 키 추출 — 단일 값 또는 priority 필드
        String decisionKey = extractDecisionKey(decision);
        if (decisionKey == null) return null;

        // 우선순위 — monologues > outcomes > reactions
        for (String mapKey : new String[]{"monologues", "outcomes", "reactions"}) {
            Object map = scene.extras().get(mapKey);
            if (map instanceof Map<?, ?> m) {
                Object v = m.get(decisionKey);
                if (v != null) return v.toString();
            }
        }
        return null;
    }

    /**
     * 결정 페이로드에서 매칭 키 추출:
     * <ul>
     *   <li>{ "value": "save_33" } → "save_33"</li>
     *   <li>{ "priority": "farmer" } → "farmer"</li>
     *   <li>{ "save_33": true } → "save_33"</li>
     *   <li>단일 string 형태 (decision 이 {"_": "reveal"} 같은 wrapper) → 값</li>
     * </ul>
     */
    private static String extractDecisionKey(Map<String, Object> decision) {
        // 1) priority 필드 (Scene 3 distribute 패턴)
        Object priority = decision.get("priority");
        if (priority instanceof String s && !s.isBlank()) return s;

        // 2) value 필드
        Object value = decision.get("value");
        if (value instanceof String s && !s.isBlank()) return s;

        // 3) 단일 entry 의 key (Controller 가 raw string 을 받았을 때 wrapper)
        if (decision.size() == 1) {
            Map.Entry<String, Object> e = decision.entrySet().iterator().next();
            Object v = e.getValue();
            if (v instanceof String s) return s;       // {"_": "reveal"}
            return e.getKey();                          // {"save_33": true}
        }
        return null;
    }

    public record Input(
            int sceneId,
            Map<String, Object> decision,
            Map<String, Object> interactionMeta,
            String mode
    ) {}

    public record Result(UUID sessionId, int previousScene, int currentScene,
                          Map<String, Object> scenePayload, String responseText) {}
}
