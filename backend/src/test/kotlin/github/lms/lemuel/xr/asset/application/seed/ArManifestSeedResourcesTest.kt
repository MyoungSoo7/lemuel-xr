package github.lms.lemuel.xr.asset.application.seed

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * 실제 시드 리소스(resources 아래 manifests 전체)에 대한 계약 테스트.
 *
 * 정책 게이트([github.lms.lemuel.xr.asset.application.XrModePolicy])는 "AR 은 에셋 있는
 * 미션만" 을 *요청 시점* 에 막는다. 이 테스트는 *데이터 쪽* 에서 같은 사실을 지킨다 —
 * AR 시드가 없는 미션에 게이트만 열리거나, 반대로 시드만 슬쩍 들어오면
 * 그 순간 게이트는 설정 한 줄 차이로 무너진다.
 */
class ArManifestSeedResourcesTest {

    private val json = ObjectMapper()
    private val resolver = PathMatchingResourcePatternResolver()

    /** AR 을 여는 미션 — application.yml `lemuel.xr.ar-enabled-missions` 기본값과 같다. */
    private val arMissions =
        setOf("joseph", "moses", "david", "jesus", "elijah", "solomon", "job")

    /** 패스스루가 있는 기기만. web 은 대상이 아니다. */
    private val arDevices = setOf("quest3", "visionpro", "galaxyxr")

    private data class Doc(val path: String, val body: Map<*, *>) {
        val mission get() = body["mission_id"] as String
        val device get() = body["device_type"] as String
        val scene get() = body["scene_number"]
        val mode get() = body["xr_mode"] as String?
        val coords get() = listOf(mission, device, scene)
        val totalSize get() = (body["total_size_bytes"] as Number).toLong()

        fun assetIds(): List<String> =
            (body["manifest"] as Map<*, *>).values
                .filterIsInstance<List<*>>()
                .flatten()
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { it["id"] as? String }
    }

    private val docs: List<Doc> by lazy {
        resolver.getResources("classpath*:manifests/**/*.json").map { r ->
            Doc(r.url.path, r.inputStream.use { json.readValue(it, Map::class.java) })
        }
    }

    private val ar by lazy { docs.filter { it.mode == "ar" } }

    @Test
    fun `AR 시드는 게이트가 연 미션에만 존재한다`() {
        assertThat(ar.map { it.mission }.toSet()).isEqualTo(arMissions)
    }

    @Test
    fun `AR 은 VR 과 좌표가 정확히 일치한다 — 씬 하나만 비어도 그 씬에서 멈춘다`() {
        val vrCoords = docs.filter { it.mode == null && it.device in arDevices }
            .filter { it.mission in arMissions }
            .map { it.coords }.toSet()

        assertThat(ar.map { it.coords }.toSet()).isEqualTo(vrCoords)
        // (요셉 5 + 모세 6 + 다윗 6 + 예수 7 + 엘리야 5 + 솔로몬 5 + 욥 5) x 3 기기
        assertThat(ar).hasSize(117)
    }

    @Test
    fun `AR manifest 는 패스스루 평면인식 앵커를 요구한다`() {
        ar.forEach { d ->
            val caps = d.body["capabilities_min"] as Map<*, *>
            assertThat(caps["passthrough"]).describedAs("%s", d.path).isEqualTo(true)
            assertThat(caps["plane_detection"]).isEqualTo(true)
            assertThat(caps["anchors"]).isEqualTo(true)
        }
    }

    @Test
    fun `AR manifest 에 환경 모델이 남아 있으면 실패 — 진짜 벽 위에 가짜 벽이 겹친다`() {
        ar.forEach { d ->
            assertThat(d.assetIds().filter { it.startsWith("env_") || it.endsWith("_far") })
                .describedAs("mission=%s device=%s scene=%s", d.mission, d.device, d.scene)
                .isEmpty()
        }
    }

    /**
     * AR 은 VR 에서 *덜어내* 만든다. 그러니 AR 에만 있는 자산은 AR 전용으로 더한
     * 두 개뿐이어야 하고, 용량은 반드시 줄어야 한다. 생성기가 엉뚱한 걸 집어넣거나
     * 덜어내기를 건너뛰면 여기서 걸린다.
     */
    @Test
    fun `AR 은 VR 의 부분집합이고 더 가볍다`() {
        val added = setOf("script_ar_anchor_placement", "shader_ar_passthrough_dim")
        val vrByCoords = docs.filter { it.mode == null }.associateBy { it.coords }

        ar.forEach { d ->
            val vr = requireNotNull(vrByCoords[d.coords]) { "VR 원본 없음: ${d.path}" }
            assertThat(d.assetIds() - added)
                .describedAs("AR 에만 있는 자산 — %s", d.path)
                .isSubsetOf(vr.assetIds())
            assertThat(d.totalSize).describedAs("%s", d.path).isLessThan(vr.totalSize)
        }
    }

    @Test
    fun `모드를 안 적은 기존 시드는 그대로 VR 로 남는다`() {
        val noMode = docs.filter { it.mode == null }

        assertThat(noMode).isNotEmpty() // 기존 시드가 사라지지 않았다
        assertThat(noMode.map { it.mission }.toSet()).contains("joseph", "moses", "david")
        assertThat(noMode.map { it.device }).contains("web") // web 은 VR 전용으로 남는다
    }
}
