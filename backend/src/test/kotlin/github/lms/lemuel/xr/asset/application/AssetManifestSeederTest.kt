package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort
import github.lms.lemuel.xr.asset.application.seed.ManifestDocument
import github.lms.lemuel.xr.asset.application.seed.ManifestParser
import github.lms.lemuel.xr.asset.application.seed.ManifestScanner
import github.lms.lemuel.xr.asset.application.seed.ManifestValidator
import github.lms.lemuel.xr.asset.domain.AssetManifest
import github.lms.lemuel.xr.asset.domain.XrMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource

/**
 * AssetManifestSeeder 단위 테스트 — 스캔·파싱·검증·존재확인을 엮는 오케스트레이션 분기 커버:
 * (1) 유효+신규 → save, (2) 필수필드 누락 → 스킵, (3) 이미 존재 → 스킵, (4) 파싱 예외 → 로그 후 계속.
 */
class AssetManifestSeederTest {

    private val manifests: AssetManifestPort = mock()
    private val scanner: ManifestScanner = mock()
    private val parser: ManifestParser = mock()

    // real validator — 검증 분기까지 실제로 태운다.
    private val validator = ManifestValidator()

    private fun doc(
        mission: String?,
        device: String?,
        version: String?,
        xrMode: String? = null,
    ): ManifestDocument =
        ManifestDocument(
            mission, 1.toShort(), device, xrMode, version,
            mapOf("webgl" to 2), mapOf("bundle" to "s.glb"), "ko", 2048L, "https://cdn",
        )

    private fun seeder(): AssetManifestSeeder =
        AssetManifestSeeder(manifests, scanner, parser, validator)

    @Test
    fun `유효하고 신규면 save 호출 그리고 도메인 매핑`() {
        val r: Resource = ClassPathResource("test-manifests/valid/scene1-v1.0.0.json")
        whenever(scanner.scan()).thenReturn(arrayOf(r))
        whenever(parser.parse(r)).thenReturn(doc("joseph", "web", "1.0.0"))
        whenever(manifests.existsByCoordinates("joseph", 1.toShort(), "web", XrMode.VR, "1.0.0"))
            .thenReturn(false)

        seeder().seed()

        val saved = argumentCaptor<AssetManifest>()
        verify(manifests).save(saved.capture())
        val m = saved.firstValue
        assertThat(m.id).isNotNull()
        assertThat(m.missionId).isEqualTo("joseph")
        assertThat(m.deviceType).isEqualTo("web")
        assertThat(m.version).isEqualTo("1.0.0")
        assertThat(m.xrMode).isEqualTo(XrMode.VR) // xr_mode 없는 기존 시드는 VR
        assertThat(m.active).isTrue()
        assertThat(m.createdAt).isNotNull()
        assertThat(m.supersededAt).isNull()
    }

    @Test
    fun `필수필드 누락이면 스킵 save 없음`() {
        val r: Resource = ClassPathResource("test-manifests/valid/scene1-v1.0.0.json")
        whenever(scanner.scan()).thenReturn(arrayOf(r))
        whenever(parser.parse(r)).thenReturn(doc("joseph", "web", null)) // version 누락

        seeder().seed()

        verify(manifests, never()).save(any())
        verify(manifests, never()).existsByCoordinates(any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun `이미 존재하면 스킵 save 없음`() {
        val r: Resource = ClassPathResource("test-manifests/valid/scene1-v1.0.0.json")
        whenever(scanner.scan()).thenReturn(arrayOf(r))
        whenever(parser.parse(r)).thenReturn(doc("joseph", "web", "1.0.0"))
        whenever(manifests.existsByCoordinates("joseph", 1.toShort(), "web", XrMode.VR, "1.0.0"))
            .thenReturn(true)

        seeder().seed()

        verify(manifests, never()).save(any())
    }

    @Test
    fun `파싱 예외면 로그후 계속 save 없음`() {
        val r: Resource = ClassPathResource("test-manifests/bad/broken.json")
        whenever(scanner.scan()).thenReturn(arrayOf(r))
        whenever(parser.parse(r)).thenThrow(RuntimeException("boom"))

        seeder().seed() // 예외를 삼키고 완료해야 함

        verify(manifests, never()).save(any())
    }

    @Test
    fun `xr_mode ar 이면 AR 좌표로 저장`() {
        val r: Resource = ClassPathResource("test-manifests/valid/scene1-v1.0.0.json")
        whenever(scanner.scan()).thenReturn(arrayOf(r))
        whenever(parser.parse(r)).thenReturn(doc("joseph", "quest3", "1.0.0", xrMode = "ar"))
        whenever(manifests.existsByCoordinates("joseph", 1.toShort(), "quest3", XrMode.AR, "1.0.0"))
            .thenReturn(false)

        seeder().seed()

        val saved = argumentCaptor<AssetManifest>()
        verify(manifests).save(saved.capture())
        assertThat(saved.firstValue.xrMode).isEqualTo(XrMode.AR)
    }

    @Test
    fun `xr_mode 오타면 스킵 — VR 로 흘려보내지 않는다`() {
        val r: Resource = ClassPathResource("test-manifests/valid/scene1-v1.0.0.json")
        whenever(scanner.scan()).thenReturn(arrayOf(r))
        whenever(parser.parse(r)).thenReturn(doc("joseph", "quest3", "1.0.0", xrMode = "AR모드"))

        seeder().seed()

        verify(manifests, never()).save(any())
        verify(manifests, never()).existsByCoordinates(any(), anyOrNull(), any(), any(), any())
    }
}
