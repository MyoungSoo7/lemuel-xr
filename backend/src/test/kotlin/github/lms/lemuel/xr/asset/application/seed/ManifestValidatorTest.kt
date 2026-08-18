package github.lms.lemuel.xr.asset.application.seed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * ManifestValidator 단위 테스트 — 필수 필드(mission_id/device_type/version) 각각의 부재 분기 커버.
 */
class ManifestValidatorTest {

    private val validator = ManifestValidator()

    private fun doc(
        missionId: String?,
        deviceType: String?,
        version: String?,
        xrMode: String? = null,
    ): ManifestDocument =
        ManifestDocument(
            missionId, 1.toShort(), deviceType, xrMode, version,
            emptyMap(), emptyMap(), "ko", 1024L, "https://cdn",
        )

    @Test
    fun `모든 필수필드 있으면 유효`() {
        assertThat(validator.isValid(doc("joseph", "web", "1.0.0"))).isTrue()
    }

    @Test
    fun `null 문서는 무효`() {
        assertThat(validator.isValid(null)).isFalse()
    }

    @Test
    fun `missionId 없으면 무효`() {
        assertThat(validator.isValid(doc(null, "web", "1.0.0"))).isFalse()
    }

    @Test
    fun `deviceType 없으면 무효`() {
        assertThat(validator.isValid(doc("joseph", null, "1.0.0"))).isFalse()
    }

    @Test
    fun `version 없으면 무효`() {
        assertThat(validator.isValid(doc("joseph", "web", null))).isFalse()
    }

    @Test
    fun `xr_mode 없으면 유효 — 모드 도입 전 시드가 그대로 산다`() {
        assertThat(validator.isValid(doc("joseph", "web", "1.0.0", xrMode = null))).isTrue()
    }

    @Test
    fun `xr_mode vr ar 은 유효 (대소문자 무시)`() {
        assertThat(validator.isValid(doc("joseph", "quest3", "1.0.0", "vr"))).isTrue()
        assertThat(validator.isValid(doc("joseph", "quest3", "1.0.0", "AR"))).isTrue()
    }

    @Test
    fun `모르는 xr_mode 는 무효`() {
        assertThat(validator.isValid(doc("joseph", "quest3", "1.0.0", "mr"))).isFalse()
    }
}
