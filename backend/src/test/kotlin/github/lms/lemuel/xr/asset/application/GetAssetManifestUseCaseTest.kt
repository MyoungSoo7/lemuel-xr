package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort
import github.lms.lemuel.xr.asset.domain.AssetManifest
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
            UUID.randomUUID(), "joseph", 1.toShort(), "web",
            emptyMap(), "1.0.0", emptyMap(), "ko", 1024L, "https://cdn",
            true, LocalDateTime.now(), null,
        )
        whenever(manifests.findLatest("joseph", 1.toShort(), "web")).thenReturn(Optional.of(manifest))

        val result = uc.getLatest("joseph", 1.toShort(), "web")

        assertThat(result).isSameAs(manifest)
    }

    @Test
    fun `getLatest 매칭없으면 E_VALIDATION 예외`() {
        whenever(manifests.findLatest("nope", 9.toShort(), "web")).thenReturn(Optional.empty())

        assertThatThrownBy { uc.getLatest("nope", 9.toShort(), "web") }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("mission=nope")
            .hasMessageContaining("device=web")
            .extracting { e -> (e as AppException).code }
            .isEqualTo(ErrorCode.E_VALIDATION)
    }
}
