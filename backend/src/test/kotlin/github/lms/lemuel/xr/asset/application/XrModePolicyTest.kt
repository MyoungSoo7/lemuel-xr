package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * XrModePolicy 단위 테스트 — "요셉만 AR" 게이트가 실제로 요셉만 통과시키는지.
 *
 * 이 게이트가 이 변경의 핵심이다. 뚫리면 다윗·모세 클라이언트가 AR 을 켜고
 * 없는 에셋을 기다린다.
 */
class XrModePolicyTest {

    private val policy = XrModePolicy("joseph") // 운영 기본값

    @Test
    fun `요셉은 AR 과 VR 둘 다`() {
        assertThat(policy.supports("joseph", XrMode.VR)).isTrue()
        assertThat(policy.supports("joseph", XrMode.AR)).isTrue()
    }

    @Test
    fun `다른 미션은 VR 만`() {
        for (mission in listOf("moses", "david", "jesus")) {
            assertThat(policy.supports(mission, XrMode.VR)).isTrue()
            assertThat(policy.supports(mission, XrMode.AR)).isFalse()
        }
    }

    @Test
    fun `모드 생략하면 VR — 기존 클라이언트는 그대로`() {
        assertThat(policy.resolve("moses", null)).isEqualTo(XrMode.VR)
        assertThat(policy.resolve("moses", "")).isEqualTo(XrMode.VR)
    }

    @Test
    fun `요셉 ar 요청은 통과 (대소문자 공백 무시)`() {
        assertThat(policy.resolve("joseph", "ar")).isEqualTo(XrMode.AR)
        assertThat(policy.resolve("JOSEPH", " AR ")).isEqualTo(XrMode.AR)
    }

    @Test
    fun `모세 ar 요청은 E_VALIDATION 으로 거부`() {
        assertThatThrownBy { policy.resolve("moses", "ar") }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("moses")
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
        val opened = XrModePolicy("joseph, moses")

        assertThat(opened.supports("moses", XrMode.AR)).isTrue()
        assertThat(opened.supports("david", XrMode.AR)).isFalse()
    }

    @Test
    fun `빈 설정이면 AR 은 전부 닫힌다`() {
        val closed = XrModePolicy("")

        assertThat(closed.supports("joseph", XrMode.AR)).isFalse()
        assertThat(closed.supports("joseph", XrMode.VR)).isTrue()
    }
}
