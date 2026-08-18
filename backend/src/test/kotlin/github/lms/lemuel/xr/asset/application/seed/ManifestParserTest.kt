package github.lms.lemuel.xr.asset.application.seed

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * ManifestParser 단위 테스트 — 유효 json 역직렬화 성공, 손상 json 은 예외 전파.
 */
class ManifestParserTest {

    private val parser = ManifestParser(ObjectMapper())

    @Test
    fun `유효한 json 은 ManifestDocument 로 파싱`() {
        val doc = parser.parse(
            ClassPathResource("test-manifests/valid/scene1-v1.0.0.json"),
        )

        assertThat(doc.missionId).isEqualTo("joseph")
        assertThat(doc.sceneNumber).isEqualTo(1.toShort())
        assertThat(doc.deviceType).isEqualTo("web")
        assertThat(doc.xrMode).isNull() // 파일에 xr_mode 가 없으면 null → 시더가 VR 로 해석
        assertThat(doc.version).isEqualTo("1.0.0")
        assertThat(doc.audioLocale).isEqualTo("ko")
        assertThat(doc.totalSizeBytes).isEqualTo(2048L)
        assertThat(doc.cdnBaseUrl).isEqualTo("https://cdn.example/joseph")
        assertThat(doc.manifest).containsEntry("bundle", "scene1.glb")
    }

    @Test
    fun `손상된 json 은 예외 전파`() {
        assertThatThrownBy {
            parser.parse(ClassPathResource("test-manifests/bad/broken.json"))
        }.isInstanceOf(Exception::class.java)
    }
}
