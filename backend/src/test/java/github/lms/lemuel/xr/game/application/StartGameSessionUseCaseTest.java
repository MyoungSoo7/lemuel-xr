package github.lms.lemuel.xr.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartGameSessionUseCaseTest {

    @Mock GameSessionRepository sessions;
    @Mock ScenarioYamlLoader loader;
    @InjectMocks StartGameSessionUseCase uc;

    private Scenario.Scene scene(int id, String type, String interaction, Integer next,
                                 Boolean llm, Map<String, Object> extras) {
        return new Scenario.Scene(id, "제목" + id, type, interaction, 60,
                "narr" + id, "ref" + id, llm, next, extras);
    }

    @Test
    void execute_는_scene1_payload_와_세션영속() {
        Scenario scenario = new Scenario("joseph", "곡식 7년", List.of(
                scene(1, "cinematic", null, 2, null, Map.of("options", List.of("a", "b"))),
                scene(2, "interaction", "pick_one", null, null, Map.of())));
        when(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario);

        UUID uid = UUID.randomUUID();
        var result = uc.execute(uid, Character.JOSEPH,
                new StartGameSessionUseCase.Input("emotional", "quest3",
                        Map.of("hmd", true), 42L));

        assertThat(result.currentScene()).isEqualTo(1);
        assertThat(result.totalScenes()).isEqualTo(2);
        assertThat(result.appliedMode()).isEqualTo("emotional");
        assertThat(result.scenePayload()).containsEntry("sceneId", 1)
                .containsEntry("type", "cinematic")
                .containsEntry("next", 2)
                .containsEntry("options", List.of("a", "b"));
        assertThat(result.sessionId()).isNotNull();

        ArgumentCaptor<GameSessionJpaEntity> cap = ArgumentCaptor.forClass(GameSessionJpaEntity.class);
        verify(sessions).save(cap.capture());
        GameSessionJpaEntity saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(uid);
        assertThat(saved.getCharacter()).isEqualTo("joseph");
        assertThat(saved.getChosenDimension()).isEqualTo("emotional");
        assertThat(saved.getDeviceType()).isEqualTo("quest3");
        assertThat(saved.getCapabilities()).containsEntry("hmd", true);
        assertThat(saved.getTriggeredByEmotionLogId()).isEqualTo(42L);
    }

    @Test
    void buildScenePayload_는_null_필드_생략하고_realtimeLlm_추가() {
        Scenario scenario = new Scenario("moses", "광야", List.of(
                scene(1, "interaction", null, null, Boolean.TRUE, null)));
        Map<String, Object> p = StartGameSessionUseCase.buildScenePayload(scenario, 1);
        assertThat(p).containsEntry("sceneId", 1)
                .containsEntry("type", "interaction")
                .containsEntry("realtimeLlm", true);
        assertThat(p).containsKey("narrationId").containsKey("scriptureRef").containsKey("durationSec");
        // interaction 은 null 이므로 생략
        assertThat(p).doesNotContainKey("interaction");
        assertThat(p.get("next")).isNull();
    }

    @Test
    void buildScenePayload_realtimeLlm_false_면_키_없음() {
        Scenario scenario = new Scenario("david", "시편", List.of(
                scene(1, "cinematic", "distribute", 2, Boolean.FALSE, Map.of())));
        Map<String, Object> p = StartGameSessionUseCase.buildScenePayload(scenario, 1);
        assertThat(p).doesNotContainKey("realtimeLlm");
        assertThat(p).containsEntry("interaction", "distribute");
    }
}
