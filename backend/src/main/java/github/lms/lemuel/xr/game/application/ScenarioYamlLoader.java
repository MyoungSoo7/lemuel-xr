package github.lms.lemuel.xr.game.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.game.domain.Character;
import github.lms.lemuel.xr.game.domain.Scenario;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * resources/scenarios/{character}.yml 로딩.
 *
 * <p>부팅 시 모두 메모리에 로드, runtime 에는 in-memory lookup. 시나리오 변경 시 재배포.
 * Phase 2 에서 hot reload 또는 DB 로 이전.</p>
 */
@Component
@Slf4j
public class ScenarioYamlLoader {

    private final Map<Character, Scenario> cache = new EnumMap<>(Character.class);

    @PostConstruct
    void loadAll() {
        for (Character c : Character.values()) {
            String path = "scenarios/" + c.dbValue() + ".yml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    log.warn("시나리오 yml 없음 — {} 건너뜀: {}", c, path);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> root = (Map<String, Object>) new Yaml().loadAs(
                        new java.io.InputStreamReader(in, StandardCharsets.UTF_8), Map.class);
                cache.put(c, toScenario(root));
                log.info("시나리오 로드 완료 — {} ({} scenes)", c, cache.get(c).scenes().size());
            } catch (Exception e) {
                log.error("시나리오 로드 실패 — {}: {}", c, e.getMessage(), e);
            }
        }
    }

    public Scenario forCharacter(Character c) {
        Scenario s = cache.get(c);
        if (s == null) throw new AppException(ErrorCode.E_CHARACTER_UNKNOWN, "Scenario not loaded: " + c);
        return s;
    }

    @SuppressWarnings("unchecked")
    private Scenario toScenario(Map<String, Object> root) {
        String character = str(root, "character");
        String title = str(root, "title");
        List<Map<String, Object>> sceneMaps = (List<Map<String, Object>>) root.get("scenes");
        List<Scenario.Scene> scenes = new ArrayList<>();
        for (Map<String, Object> sm : sceneMaps) {
            Map<String, Object> extras = new HashMap<>(sm);
            // 정의된 표준 필드는 extras 에서 제거 → 나머지만 extras 로 보존
            extras.remove("id");
            extras.remove("title");
            extras.remove("type");
            extras.remove("interaction");
            extras.remove("duration_sec");
            extras.remove("narration_id");
            extras.remove("scripture_ref");
            extras.remove("realtime_llm");
            extras.remove("next");
            scenes.add(new Scenario.Scene(
                    (int) sm.get("id"),
                    str(sm, "title"),
                    str(sm, "type"),
                    str(sm, "interaction"),
                    (Integer) sm.get("duration_sec"),
                    str(sm, "narration_id"),
                    str(sm, "scripture_ref"),
                    (Boolean) sm.get("realtime_llm"),
                    (Integer) sm.get("next"),
                    extras
            ));
        }
        return new Scenario(character, title, scenes);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }
}
