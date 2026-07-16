package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.Scenario
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.EnumMap

/**
 * resources/scenarios/{character}.yml 로딩.
 *
 * 부팅 시 모두 메모리에 로드, runtime 에는 in-memory lookup. 시나리오 변경 시 재배포.
 * Phase 2 에서 hot reload 또는 DB 로 이전.
 */
@Component
class ScenarioYamlLoader {

    private val cache: MutableMap<Character, Scenario> = EnumMap(Character::class.java)

    @PostConstruct
    fun loadAll() {
        for (c in Character.entries) {
            val path = "scenarios/${c.dbValue}.yml"
            try {
                javaClass.classLoader.getResourceAsStream(path).use { input ->
                    if (input == null) {
                        log.warn("시나리오 yml 없음 — {} 건너뜀: {}", c, path)
                        return@use
                    }
                    @Suppress("UNCHECKED_CAST")
                    val root = Yaml().loadAs(
                        InputStreamReader(input, StandardCharsets.UTF_8), Map::class.java,
                    ) as Map<String, Any?>
                    cache[c] = toScenario(root)
                    log.info("시나리오 로드 완료 — {} ({} scenes)", c, cache[c]!!.scenes.size)
                }
            } catch (e: Exception) {
                log.error("시나리오 로드 실패 — {}: {}", c, e.message, e)
            }
        }
    }

    fun forCharacter(c: Character): Scenario =
        cache[c] ?: throw AppException(ErrorCode.E_CHARACTER_UNKNOWN, "Scenario not loaded: $c")

    private fun toScenario(root: Map<String, Any?>): Scenario {
        val character = str(root, "character")
        val title = str(root, "title")
        @Suppress("UNCHECKED_CAST")
        val sceneMaps = root["scenes"] as List<Map<String, Any?>>
        val scenes = ArrayList<Scenario.Scene>()
        for (sm in sceneMaps) {
            val extras = HashMap<String, Any?>(sm)
            // 정의된 표준 필드는 extras 에서 제거 → 나머지만 extras 로 보존
            extras.remove("id")
            extras.remove("title")
            extras.remove("type")
            extras.remove("interaction")
            extras.remove("duration_sec")
            extras.remove("narration_id")
            extras.remove("scripture_ref")
            extras.remove("realtime_llm")
            extras.remove("next")
            scenes.add(
                Scenario.Scene(
                    sm["id"] as Int,
                    str(sm, "title"),
                    str(sm, "type"),
                    str(sm, "interaction"),
                    sm["duration_sec"] as Int?,
                    str(sm, "narration_id"),
                    str(sm, "scripture_ref"),
                    sm["realtime_llm"] as Boolean?,
                    sm["next"] as Int?,
                    extras,
                ),
            )
        }
        return Scenario(character, title, scenes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScenarioYamlLoader::class.java)

        private fun str(m: Map<String, Any?>, k: String): String? = m[k]?.toString()
    }
}
