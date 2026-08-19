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

    private val policy =
        XrModePolicy("joseph,moses,david,jesus,elijah,solomon,job") // 운영 기본값

    @Test
    fun `AR manifest 가 있는 일곱 미션은 AR 과 VR 둘 다`() {
        for (mission in
            listOf("joseph", "moses", "david", "jesus", "elijah", "solomon", "job")
        ) {
            assertThat(policy.supports(mission, XrMode.VR)).isTrue()
            assertThat(policy.supports(mission, XrMode.AR)).isTrue()
        }
    }

    /**
     * 룻은 시나리오가 열려 있어도 AR 은 닫혀 있다 — 저작이 덜 된 게 아니라 **결정** 이다.
     *
     * 패스스루는 씬을 사용자의 실제 방에 놓는다. Scene 4(밤·권력 비대칭)를 그렇게 놓는 것이
     * VR 과 같은 노출인지에 답할 사람이 없었고, 답 없이 열지 않는 쪽을 골랐다
     * (`docs/RUTH-RUNTIME-SIGNOFF.md`). 라합의 "아직 못 만들었다" 와 구분해서 못 박아 둔다 —
     * 둘 다 `supports(..., AR) == false` 지만 되돌리는 조건이 다르다.
     */
    @Test
    fun `룻은 VR 만 — 저작이 아니라 판단 때문에 AR 이 닫혀 있다`() {
        assertThat(policy.supports("ruth", XrMode.VR)).isTrue()
        assertThat(policy.supports("ruth", XrMode.AR)).isFalse()
    }

    @Test
    fun `에셋 없는 미션은 VR 만 — 씬 manifest 가 아직 없다`() {
        // 라합은 아직 저작 중이라 씬 manifest 가 없다 — VR 은 열려 있지만 AR 은 닫혀 있다.
        // (VR 이 열려 있다는 것은 조회가 400 이 아니라는 뜻이고, manifest 가 없으면 404 다.)
        assertThat(policy.supports("rahab", XrMode.VR)).isTrue()
        assertThat(policy.supports("rahab", XrMode.AR)).isFalse()
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
        assertThatThrownBy { policy.resolve("rahab", "ar") }
            .isInstanceOf(AppException::class.java)
            .hasMessageContaining("rahab")
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
        val opened = XrModePolicy("joseph, moses, rahab")

        assertThat(opened.supports("rahab", XrMode.AR)).isTrue()
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
