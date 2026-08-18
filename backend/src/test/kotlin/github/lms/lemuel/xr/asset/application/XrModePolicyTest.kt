package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * XrModePolicy 단위 테스트 — AR 게이트가 *에셋이 있는 미션만* 통과시키는지.
 *
 * 이 게이트가 이 변경의 핵심이다. 뚫리면 에셋 없는 미션의 클라이언트가 AR 을 켜고
 * 오지 않을 manifest 를 기다린다.
 */
class XrModePolicyTest {

    private val policy = XrModePolicy("joseph,moses,david,jesus") // 운영 기본값

    @Test
    fun `AR manifest 가 있는 네 미션은 AR 과 VR 둘 다`() {
        for (mission in listOf("joseph", "moses", "david", "jesus")) {
            assertThat(policy.supports(mission, XrMode.VR)).isTrue()
            assertThat(policy.supports(mission, XrMode.AR)).isTrue()
        }
    }

    @Test
    fun `에셋 없는 미션은 VR 만 — 씬 manifest 가 아직 없다`() {
        for (mission in listOf("ruth", "solomon", "elijah", "job")) {
            assertThat(policy.supports(mission, XrMode.VR)).isTrue()
            assertThat(policy.supports(mission, XrMode.AR)).isFalse()
        }
    }

    @Test
    fun `모드 생략하면 VR — 기존 클라이언트는 그대로`() {
        assertThat(policy.resolve("jesus", null)).isEqualTo(XrMode.VR)
        assertThat(policy.resolve("jesus", "")).isEqualTo(XrMode.VR)
    }

    @Test
    fun `ar 요청은 통과 (대소문자 공백 무시)`() {
        assertThat(policy.resolve("joseph", "ar")).isEqualTo(XrMode.AR)
        assertThat(policy.resolve("DAVID", " AR ")).isEqualTo(XrMode.AR)
        assertThat(policy.resolve("Jesus", "AR")).isEqualTo(XrMode.AR)
    }

    @Test
    fun `에셋 없는 미션의 ar 요청은 E_VALIDATION 으로 거부`() {
        assertThatThrownBy { policy.resolve("elijah", "ar") }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("elijah")
            .hasMessageContaining("ar")
            .extracting { e -> (e as AppException).code }
            .isEqualTo(ErrorCode.E_VALIDATION)
    }

    @Test
    fun `모르는 모드 값은 E_VALIDATION`() {
        assertThatThrownBy { policy.resolve("joseph", "mr") }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("Unknown xr mode")
    }

    @Test
    fun `설정으로 다른 미션도 열 수 있다 — 코드 수정 없이`() {
        val opened = XrModePolicy("joseph, moses, elijah")

        assertThat(opened.supports("elijah", XrMode.AR)).isTrue()
        assertThat(opened.supports("david", XrMode.AR)).isFalse() // 목록에 없으면 닫힌다
        assertThat(opened.supports("jesus", XrMode.AR)).isFalse()
    }

    @Test
    fun `빈 설정이면 AR 은 전부 닫힌다`() {
        val closed = XrModePolicy("")

        assertThat(closed.supports("joseph", XrMode.AR)).isFalse()
        assertThat(closed.supports("joseph", XrMode.VR)).isTrue()
    }
}
