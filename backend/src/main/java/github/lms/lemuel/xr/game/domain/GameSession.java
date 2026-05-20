package github.lms.lemuel.xr.game.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 세션 도메인.
 *
 * <p>한 세션 = 한 캐릭터 1회 플레이. decisions 에 Scene 별 사용자 선택 기록.
 * Scene 4 에서 *Scene 3 분배 패턴* 을 참조해 실시간 LLM 프롬프트를 구성한다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {
    private UUID id;
    private UUID userId;
    private String character;            // "joseph" (MVP)
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Map<String, Object> decisions = new HashMap<>();
    private String finalOutcome;          // "farmer_first" | "immigrant_first" | "merchant_first"

    public static GameSession start(UUID userId, String character) {
        GameSession s = new GameSession();
        s.id = UUID.randomUUID();
        s.userId = userId;
        s.character = character;
        s.startedAt = LocalDateTime.now();
        return s;
    }

    public void recordDecision(int sceneId, String value) {
        decisions.put("scene" + sceneId, value);
    }

    public void recordDecision(int sceneId, Object value) {
        decisions.put("scene" + sceneId, value);
    }

    public void complete(String outcome) {
        this.finalOutcome = outcome;
        this.completedAt = LocalDateTime.now();
    }
}
