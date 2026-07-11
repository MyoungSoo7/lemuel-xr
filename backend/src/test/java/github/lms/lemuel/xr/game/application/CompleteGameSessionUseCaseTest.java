package github.lms.lemuel.xr.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompleteGameSessionUseCaseTest {

    @Mock GameSessionRepository sessions;
    @Mock ScenarioYamlLoader scenarios;
    @InjectMocks CompleteGameSessionUseCase uc;

    private GameSessionJpaEntity live(UUID id, String character, LocalDateTime started) {
        GameSessionJpaEntity e = new GameSessionJpaEntity();
        e.setId(id);
        e.setCharacter(character);
        e.setStartedAt(started);
        return e;
    }

    private Scenario.Scene outro(Integer next, Map<String, Object> extras) {
        return new Scenario.Scene(5, "결말", "outro", null, null, null, "gen-45:5", false, next, extras);
    }

    @Test
    void execute_는_완료시각_duration_valuePrompt_반환() {
        UUID sid = UUID.randomUUID();
        LocalDateTime started = LocalDateTime.now().minusSeconds(120);
        when(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "joseph", started)));
        Scenario scenario = new Scenario("joseph", "곡식 7년", List.of(
                outro(null, Map.of("linked_values", List.of(1, 2, 7),
                        "value_prompt", "오늘 3줄 일기"))));
        when(scenarios.forCharacter(Character.JOSEPH)).thenReturn(scenario);

        var r = uc.execute(sid, "farmer_first", "수고했어요");

        assertThat(r.completedAt()).isNotNull();
        assertThat(r.durationSeconds()).isGreaterThanOrEqualTo(119);
        assertThat(r.valuePrompt()).isNotNull();
        assertThat(r.valuePrompt().suggestedValueIds()).containsExactly(1, 2, 7);
        assertThat(r.valuePrompt().message()).isEqualTo("오늘 3줄 일기");
        assertThat(r.valuePrompt().linkedCharacter()).isEqualTo("joseph");
    }

    @Test
    void execute_outro가_next_null_없으면_마지막scene_fallback() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "moses", LocalDateTime.now())));
        // 모든 scene next != null → 마지막 scene fallback 경로
        Scenario.Scene s1 = new Scenario.Scene(1, "a", "cinematic", null, null, null, null, false, 2, Map.of());
        Scenario.Scene s2 = new Scenario.Scene(2, "b", "outro", null, null, null, null, false, 3,
                Map.of("value_prompt", "광야의 실천"));
        when(scenarios.forCharacter(Character.MOSES)).thenReturn(new Scenario("moses", "t", List.of(s1, s2)));

        var r = uc.execute(sid, "out", null);
        assertThat(r.valuePrompt()).isNotNull();
        assertThat(r.valuePrompt().message()).isEqualTo("광야의 실천");
        assertThat(r.valuePrompt().suggestedValueIds()).isEmpty();
    }

    @Test
    void execute_linked_values도_prompt도_없으면_valuePrompt_null() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "david", LocalDateTime.now())));
        when(scenarios.forCharacter(Character.DAVID)).thenReturn(
                new Scenario("david", "t", List.of(outro(null, Map.of("other", "x")))));

        var r = uc.execute(sid, "out", null);
        assertThat(r.valuePrompt()).isNull();
    }

    @Test
    void execute_extras_null_이면_valuePrompt_null() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.of(live(sid, "david", LocalDateTime.now())));
        when(scenarios.forCharacter(Character.DAVID)).thenReturn(
                new Scenario("david", "t", List.of(outro(null, null))));

        var r = uc.execute(sid, "out", null);
        assertThat(r.valuePrompt()).isNull();
    }

    @Test
    void execute_알수없는_character면_valuePrompt_null_graceful() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid, "unknown_char", LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        // Character.from 이 예외 → catch → null
        var r = uc.execute(sid, "out", null);
        assertThat(r.valuePrompt()).isNull();
    }

    @Test
    void execute_startedAt_null_이면_duration_null() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid, "david", null);
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        when(scenarios.forCharacter(Character.DAVID)).thenReturn(
                new Scenario("david", "t", List.of(outro(null, Map.of()))));
        var r = uc.execute(sid, "out", null);
        assertThat(r.durationSeconds()).isNull();
    }

    @Test
    void execute_세션없음_E_SESSION_NOT_FOUND() {
        UUID sid = UUID.randomUUID();
        when(sessions.findById(sid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> uc.execute(sid, "out", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND);
    }

    @Test
    void execute_이미완료_E_SESSION_INVALID() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid, "joseph", LocalDateTime.now());
        e.setCompletedAt(LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> uc.execute(sid, "out", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID);
    }

    @Test
    void execute_abandoned_E_SESSION_INVALID() {
        UUID sid = UUID.randomUUID();
        GameSessionJpaEntity e = live(sid, "joseph", LocalDateTime.now());
        e.setAbandonedAt(LocalDateTime.now());
        when(sessions.findById(sid)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> uc.execute(sid, "out", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID);
    }
}
