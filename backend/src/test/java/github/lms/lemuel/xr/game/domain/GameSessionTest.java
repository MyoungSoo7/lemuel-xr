package github.lms.lemuel.xr.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameSessionTest {

    @Test
    void start_는_id_userId_character_startedAt_초기화() {
        UUID uid = UUID.randomUUID();
        GameSession s = GameSession.start(uid, "joseph");
        assertThat(s.getId()).isNotNull();
        assertThat(s.getUserId()).isEqualTo(uid);
        assertThat(s.getCharacter()).isEqualTo("joseph");
        assertThat(s.getStartedAt()).isNotNull();
        assertThat(s.getCompletedAt()).isNull();
        assertThat(s.getDecisions()).isEmpty();
    }

    @Test
    void recordDecision_string() {
        GameSession s = GameSession.start(UUID.randomUUID(), "moses");
        s.recordDecision(2, "save_33");
        assertThat(s.getDecisions()).containsEntry("scene2", "save_33");
    }

    @Test
    void recordDecision_object() {
        GameSession s = GameSession.start(UUID.randomUUID(), "david");
        Map<String, Object> payload = Map.of("priority", "farmer");
        s.recordDecision(3, (Object) payload);
        assertThat(s.getDecisions()).containsEntry("scene3", payload);
    }

    @Test
    void complete_는_finalOutcome_completedAt_설정() {
        GameSession s = GameSession.start(UUID.randomUUID(), "jesus");
        s.complete("immigrant_first");
        assertThat(s.getFinalOutcome()).isEqualTo("immigrant_first");
        assertThat(s.getCompletedAt()).isNotNull();
    }
}
