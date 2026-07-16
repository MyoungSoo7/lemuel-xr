package github.lms.lemuel.xr.game.domain

/** YAML 시나리오 모델. ScenarioYamlLoader 가 채워줌. */
data class Scenario(
    val character: String?,
    val title: String?,
    val scenes: List<Scene>,
) {

    fun scene(id: Int): Scene =
        scenes.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Scene not found: $id")

    fun totalScenes(): Int = scenes.size

    /** YAML 한 씬. type 별 추가 필드 (options, queues, monologue_cache_keys 등) 는 extras 로. */
    data class Scene(
        val id: Int,
        val title: String?,
        /** cinematic | interaction | outro */
        val type: String?,
        /** pick_one | distribute | null */
        val interaction: String?,
        val durationSec: Int?,
        val narrationId: String?,
        val scriptureRef: String?,
        val realtimeLlm: Boolean?,
        val next: Int?,
        /** options/queues/cache_keys 등 */
        val extras: Map<String, Any?>?,
    )
}
