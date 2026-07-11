package github.lms.lemuel.xr.game.domain;

import java.util.List;
import java.util.Map;

/** YAML 시나리오 모델. ScenarioYamlLoader 가 채워줌. */
public record Scenario(
        String character,
        String title,
        List<Scene> scenes
) {

    public Scene scene(int id) {
        return scenes.stream()
                .filter(s -> s.id() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
    }

    public int totalScenes() { return scenes.size(); }

    /** YAML 한 씬. type 별 추가 필드 (options, queues, monologue_cache_keys 등) 는 extras 로. */
    public record Scene(
            int id,
            String title,
            String type,                 // cinematic | interaction | outro
            String interaction,          // pick_one | distribute | null
            Integer durationSec,
            String narrationId,
            String scriptureRef,
            Boolean realtimeLlm,
            Integer next,
            Map<String, Object> extras   // options/queues/cache_keys 등
    ) {}
}
