package github.lms.lemuel.xr.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecideSceneUseCaseTest {

    @Mock GameSessionRepository sessions;
    @Mock GameDecisionRepository decisions;
    @Mock ScenarioYamlLoader loader;
    @Mock GenerateLlmResponseUseCase llm;
    @InjectMocks DecideSceneUseCase uc;

    private Scenario.Scene scene(int id, Integer next, Boolean llmFlag, Map<String, Object> extras) {
        return new Scenario.Scene(id, "장면" + id, "interaction", "pick_one", 60,
                "narr", "ref", llmFlag, next, extras);
    }

    private GameSessionJpaEntity liveSession(UUID id, String character, String dimension) {
        GameSessionJpaEntity e = new GameSessionJpaEntity();
        e.setId(id);
        e.setCharacter(character);
        e.setChosenDimension(dimension);
        e.setDecisions(new HashMap<>());
        e.setSceneCountCompleted((short) 0);
        return e;
    }

    // ── happy path: 정적 monologue lookup, next scene payload ──
    @Test
    void execute_정적_monologue_lookup_후_다음scene_payload() {
        UUID sid = UUID.randomUUID();
        Map<String, Object> monos = Map.of("save_33", "요셉이 실제로 따른 비율");
        Scenario scenario = new Scenario("joseph", "곡식 7년", List.of(
                scene(2, 3, false, Map.of("monologues", monos)),
                scene(3, null, false, Map.of())));
        when(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", "emotional")));
        when(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario);

        var r = uc.execute(sid, Character.JOSEPH, new DecideSceneUseCase.Input(
                2, Map.of("value", "save_33"), Map.of("ms", 1200), "emotional"));

        assertThat(r.previousScene()).isEqualTo(2);
        assertThat(r.currentScene()).isEqualTo(3);
        assertThat(r.responseText()).isEqualTo("요셉이 실제로 따른 비율");
        assertThat(r.scenePayload()).containsEntry("sceneId", 3);

        // decision 영속 검증
        ArgumentCaptor<GameDecisionJpaEntity> cap = ArgumentCaptor.forClass(GameDecisionJpaEntity.class);
        verify(decisions).save(cap.capture());
        assertThat(cap.getValue().getSceneNumber()).isEqualTo((short) 2);
        assertThat(cap.getValue().getSceneName()).isEqualTo("장면2");
    }

    @Test
    void execute_마지막scene_next_null_이면_end_payload() {
        UUID sid = UUID.randomUUID();
        Scenario scenario = new Scenario("moses", "광야", List.of(
                scene(5, null, false, Map.of())));
        when(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "moses", null)));
        when(loader.forCharacter(Character.MOSES)).thenReturn(scenario);

        var r = uc.execute(sid, Character.MOSES, new DecideSceneUseCase.Input(
                5, Map.of("value", "go"), null, null));

        assertThat(r.previousScene()).isEqualTo(5);
        assertThat(r.currentScene()).isEqualTo(5);
        assertThat(r.scenePayload()).containsEntry("type", "end");
    }

    @Test
    void execute_realtime_llm_성공() {
        UUID sid = UUID.randomUUID();
        Scenario scenario = new Scenario("joseph", "t", List.of(
                scene(4, 5, Boolean.TRUE, Map.of("reactions", Map.of("reveal", "정적"))),
                scene(5, null, false, Map.of())));
        when(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)));
        when(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario);
        when(llm.execute(anyString(), anyString(), anyMap()))
                .thenReturn(new GenerateLlmResponseUseCase.Result("LLM 실시간 응답", "openai", "gpt", false));

        var r = uc.execute(sid, Character.JOSEPH, new DecideSceneUseCase.Input(
                4, Map.of("priority", "reveal"), null, null));

        assertThat(r.responseText()).isEqualTo("LLM 실시간 응답");
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(llm).execute(anyString(), keyCap.capture(), anyMap());
        assertThat(keyCap.getValue()).isEqualTo("joseph.s4.reaction");
    }

    @Test
    void execute_realtime_llm_실패시_정적_fallback() {
        UUID sid = UUID.randomUUID();
        Scenario scenario = new Scenario("joseph", "t", List.of(
                scene(4, 5, Boolean.TRUE, Map.of("reactions", Map.of("reveal", "정적 fallback 텍스트"))),
                scene(5, null, false, Map.of())));
        when(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)));
        when(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario);
        when(llm.execute(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("sidecar down"));

        var r = uc.execute(sid, Character.JOSEPH, new DecideSceneUseCase.Input(
                4, Map.of("value", "reveal"), null, null));

        assertThat(r.responseText()).isEqualTo("정적 fallback 텍스트");
    }

    // ── 에러 분기 ──
    @Test
    void execute_세션없음_E_SESSION_NOT_FOUND() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> uc.execute(sid, Character.JOSEPH,
                new DecideSceneUseCase.Input(2, Map.of("value", "x"), null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND);
    }

    @Test
    void execute_완료된세션_E_SESSION_INVALID() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = liveSession(sid, "joseph", null);
        e.setCompletedAt(LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> uc.execute(sid, Character.JOSEPH,
                new DecideSceneUseCase.Input(2, Map.of("value", "x"), null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID);
    }

    @Test
    void execute_abandoned세션_E_SESSION_INVALID() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = liveSession(sid, "joseph", null);
        e.setAbandonedAt(LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> uc.execute(sid, Character.JOSEPH,
                new DecideSceneUseCase.Input(2, Map.of("value", "x"), null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID);
    }

    @Test
    void execute_character_불일치_E_CHARACTER_UNKNOWN() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "moses", null)));
        assertThatThrownBy(() -> uc.execute(sid, Character.JOSEPH,
                new DecideSceneUseCase.Input(2, Map.of("value", "x"), null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_CHARACTER_UNKNOWN);
    }

    @Test
    void execute_mode_불일치_E_MODE_MISMATCH() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", "rational")));
        assertThatThrownBy(() -> uc.execute(sid, Character.JOSEPH,
                new DecideSceneUseCase.Input(2, Map.of("value", "x"), null, "emotional")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_MODE_MISMATCH);
    }

    // ── matchResponseText / extractDecisionKey (static) 직접 커버 ──
    @Test
    void matchResponseText_priority_우선() {
        Scenario.Scene sc = scene(3, 4, false, Map.of("outcomes", Map.of("farmer", "이집트가 살았다")));
        assertThat(DecideSceneUseCase.matchResponseText(sc, Map.of("priority", "farmer")))
                .isEqualTo("이집트가 살았다");
    }

    @Test
    void matchResponseText_value_필드() {
        Scenario.Scene sc = scene(2, 3, false, Map.of("monologues", Map.of("save_20", "일부만")));
        assertThat(DecideSceneUseCase.matchResponseText(sc, Map.of("value", "save_20")))
                .isEqualTo("일부만");
    }

    @Test
    void matchResponseText_단일_string_wrapper() {
        Scenario.Scene sc = scene(4, 5, false, Map.of("reactions", Map.of("reveal", "밝힌다")));
        // {"_": "reveal"} → 값 reveal
        assertThat(DecideSceneUseCase.matchResponseText(sc, Map.of("_", "reveal")))
                .isEqualTo("밝힌다");
    }

    @Test
    void matchResponseText_단일_key_true_형태() {
        Scenario.Scene sc = scene(2, 3, false, Map.of("monologues", Map.of("save_33", "실제 비율")));
        // {"save_33": true} → key save_33
        Map<String, Object> d = new HashMap<>();
        d.put("save_33", true);
        assertThat(DecideSceneUseCase.matchResponseText(sc, d)).isEqualTo("실제 비율");
    }

    @Test
    void matchResponseText_extras_null_이면_null() {
        Scenario.Scene sc = new Scenario.Scene(1, "t", "cinematic", null, 60, null, null, false, 2, null);
        assertThat(DecideSceneUseCase.matchResponseText(sc, Map.of("value", "x"))).isNull();
    }

    @Test
    void matchResponseText_decision_null_이면_null() {
        Scenario.Scene sc = scene(2, 3, false, Map.of("monologues", Map.of("a", "b")));
        assertThat(DecideSceneUseCase.matchResponseText(sc, null)).isNull();
    }

    @Test
    void matchResponseText_decisionKey_추출불가_null() {
        Scenario.Scene sc = scene(2, 3, false, Map.of("monologues", Map.of("a", "b")));
        // priority/value 없고 size>1 → key 추출 불가
        Map<String, Object> d = new HashMap<>();
        d.put("x", 1);
        d.put("y", 2);
        assertThat(DecideSceneUseCase.matchResponseText(sc, d)).isNull();
    }

    @Test
    void matchResponseText_매칭없으면_null() {
        Scenario.Scene sc = scene(2, 3, false, Map.of("monologues", Map.of("save_20", "일부")));
        assertThat(DecideSceneUseCase.matchResponseText(sc, Map.of("value", "nonexistent"))).isNull();
    }
}
