package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * InputMappingResolver 단위 테스트 — 모드 축이 붙은 뒤에도 VR 동작이 그대로인지,
 * AR 오버레이가 VR 매핑 위에 얹히는지.
 */
class InputMappingResolverTest {

    private val resolver = InputMappingResolver(
        listOf(Quest3InputMapping(), VisionProInputMapping(), GalaxyXrInputMapping()),
    )

    @Test
    fun `모드 생략은 VR 매핑 그대로`() {
        assertThat(resolver.resolve("quest3"))
            .isEqualTo(resolver.resolve("quest3", XrMode.VR))
            .containsKey("GRAB")
            .doesNotContainKey("PLACE_ON_SURFACE")
    }

    @Test
    fun `AR 은 VR 매핑에 오버레이를 얹는다`() {
        val ar = resolver.resolve("quest3", XrMode.AR)

        assertThat(ar).containsKeys("GRAB", "POINT_AT") // VR 매핑 보존
        assertThat(ar).containsKeys("PLACE_ON_SURFACE", "RECENTER_ANCHOR", "LOCOMOTION")
    }

    @Test
    fun `AR 로코모션은 세 기기 모두 실제 걸음`() {
        for (device in listOf("quest3", "visionpro", "galaxyxr")) {
            @Suppress("UNCHECKED_CAST")
            val locomotion = resolver.resolve(device, XrMode.AR)["LOCOMOTION"] as Map<String, Any>
            assertThat(locomotion["binding"]).isEqualTo("physical_walk")
        }
    }

    @Test
    fun `Vision Pro AR 배치는 시선으로 겨냥하고 핀치로 확정`() {
        @Suppress("UNCHECKED_CAST")
        val place = resolver.resolve("visionpro", XrMode.AR)["PLACE_ON_SURFACE"] as Map<String, Any>

        assertThat(place["source"]).isEqualTo("eye") // 컨트롤러가 없다
        assertThat(place["confirm"]).isEqualTo(mapOf("source" to "hand", "binding" to "pinch"))
    }

    @Test
    fun `모르는 디바이스는 예외 — 모드와 무관하게`() {
        assertThatThrownBy { resolver.resolve("vive", XrMode.AR) }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("Unknown device")
    }
}
