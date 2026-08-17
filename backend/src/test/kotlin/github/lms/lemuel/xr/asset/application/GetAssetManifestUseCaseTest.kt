package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort
import github.lms.lemuel.xr.asset.domain.AssetManifest
import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

/**
 * GetAssetManifestUseCase 단위 테스트 — 조회 위임과 부재 시 예외 승격을 검증.
 */
class GetAssetManifestUseCaseTest {

    private val manifests: AssetManifestPort = mock()
    private val uc = GetAssetManifestUseCase(manifests)

    @Test
    fun `getLatest 매칭되면 도메인 반환`() {
        val manifest = AssetManifest(
            UUID.randomUUID(), "joseph", 1.toShort(), "web", XrMode.VR,
            emptyMap(), "1.0.0", emptyMap(), "ko", 1024L, "https://cdn",
            true, LocalDateTime.now(), null,
        )
        whenever(manifests.findLatest("joseph", 1.toShort(), "web", XrMode.VR))
            .thenReturn(Optional.of(manifest))

        val result = uc.getLatest("joseph", 1.toShort(), "web")

        assertThat(result).isSameAs(manifest)
    }

    @Test
    fun `getLatest 매칭없으면 E_VALIDATION 예외`() {
        whenever(manifests.findLatest("nope", 9.toShort(), "web", XrMode.VR)).thenReturn(Optional.empty())

        assertThatThrownBy { uc.getLatest("nope", 9.toShort(), "web") }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("mission=nope")
            .hasMessageContaining("device=web")
            .extracting { e -> (e as AppException).code }
            .isEqualTo(ErrorCode.E_VALIDATION)
    }

    @Test
    fun `모드는 폴백하지 않는다 — AR 없으면 VR 로 대신 주지 않고 거부`() {
        val vr = AssetManifest(
            UUID.randomUUID(), "moses", 1.toShort(), "quest3", XrMode.VR,
            emptyMap(), "1.0.0", emptyMap(), "ko", 1024L, "https://cdn",
            true, LocalDateTime.now(), null,
        )
        whenever(manifests.findLatest("moses", 1.toShort(), "quest3", XrMode.VR))
            .thenReturn(Optional.of(vr))
        whenever(manifests.findLatest("moses", 1.toShort(), "quest3", XrMode.AR))
            .thenReturn(Optional.empty())

        assertThatThrownBy { uc.getLatest("moses", 1.toShort(), "quest3", XrMode.AR) }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("mode=ar")
    }

    @Test
    fun `AR 조회는 AR manifest 를 반환`() {
        val ar = AssetManifest(
            UUID.randomUUID(), "joseph", 1.toShort(), "quest3", XrMode.AR,
            mapOf("passthrough" to true), "1.0.0", emptyMap(), "ko", 1024L, "https://cdn",
            true, LocalDateTime.now(), null,
        )
        whenever(manifests.findLatest("joseph", 1.toShort(), "quest3", XrMode.AR))
            .thenReturn(Optional.of(ar))

        assertThat(uc.getLatest("joseph", 1.toShort(), "quest3", XrMode.AR).xrMode)
            .isEqualTo(XrMode.AR)
    }
}
