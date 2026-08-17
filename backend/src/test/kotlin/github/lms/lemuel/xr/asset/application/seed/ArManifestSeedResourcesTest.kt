package github.lms.lemuel.xr.asset.application.seed

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * 실제 시드 리소스(resources 아래 manifests 전체)에 대한 계약 테스트.
 *
 * 정책 게이트([github.lms.lemuel.xr.asset.application.XrModePolicy])는 "요셉만 AR" 을
 * *요청 시점* 에 막는다. 이 테스트는 *데이터 쪽* 에서 같은 사실을 지킨다 —
 * 다른 미션에 AR 시드가 슬쩍 들어오면 정책만 열면 켜지는 상태가 되고,
 * 그 순간 "요셉만" 은 코드 한 줄 차이로 무너진다.
 */
class ArManifestSeedResourcesTest {

    private val json = ObjectMapper()
    private val resolver = PathMatchingResourcePatternResolver()

    private fun docs(): List<Pair<String, Map<*, *>>> =
        resolver.getResources("classpath*:manifests/**/*.json").map { r ->
            r.url.path to r.inputStream.use { json.readValue(it, Map::class.java) }
        }

    @Test
    fun `AR 시드는 요셉에만 존재한다`() {
        val arMissions = docs()
            .filter { (_, d) -> d["xr_mode"] == "ar" }
            .map { (_, d) -> d["mission_id"] }
            .toSet()

        assertThat(arMissions).containsExactly("joseph")
    }

    @Test
    fun `요셉 AR 은 3기기 x 5씬 이 다 있다`() {
        val ar = docs().map { it.second }.filter { it["xr_mode"] == "ar" }

        assertThat(ar).hasSize(15)
        assertThat(ar.map { it["device_type"] }.toSet())
            .containsExactlyInAnyOrder("quest3", "visionpro", "galaxyxr") // web 은 패스스루가 없다
        assertThat(ar.map { it["scene_number"] }.toSet())
            .containsExactlyInAnyOrder(1, 2, 3, 4, 5)
    }

    @Test
    fun `AR manifest 는 패스스루 평면인식 앵커를 요구한다`() {
        docs().map { it.second }.filter { it["xr_mode"] == "ar" }.forEach { d ->
            val caps = d["capabilities_min"] as Map<*, *>
            assertThat(caps["passthrough"]).describedAs("%s", d).isEqualTo(true)
            assertThat(caps["plane_detection"]).isEqualTo(true)
            assertThat(caps["anchors"]).isEqualTo(true)
        }
    }

    @Test
    fun `AR manifest 에 환경 모델이 남아 있으면 실패 — 진짜 벽 위에 가짜 벽이 겹친다`() {
        docs().map { it.second }.filter { it["xr_mode"] == "ar" }.forEach { d ->
            val manifest = d["manifest"] as Map<*, *>
            val ids = manifest.values
                .filterIsInstance<List<*>>()
                .flatten()
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { it["id"] as? String }

            assertThat(ids.filter { it.startsWith("env_") || it.endsWith("_far") })
                .describedAs("mission=%s device=%s scene=%s", d["mission_id"], d["device_type"], d["scene_number"])
                .isEmpty()
        }
    }

    @Test
    fun `모드를 안 적은 기존 시드는 그대로 VR 로 남는다`() {
        val noMode = docs().map { it.second }.filter { it["xr_mode"] == null }

        assertThat(noMode).isNotEmpty() // 기존 시드가 사라지지 않았다
        assertThat(noMode.map { it["mission_id"] }.toSet())
            .contains("joseph", "moses", "david")
    }
}
