package github.lms.lemuel.xr.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * auth/domain 순수 enum 단위 테스트 — dbValue() round-trip + from() fallback/예외 분기.
 */
class AuthDomainEnumsTest {

    @Nested
    inner class DimensionTest {
        @Test
        fun `dbValue 라운드트립`() {
            for (d in Dimension.entries) {
                assertThat(Dimension.from(d.dbValue())).isEqualTo(d)
            }
            assertThat(Dimension.SPIRITUAL.dbValue()).isEqualTo("spiritual")
            assertThat(Dimension.EMOTIONAL.dbValue()).isEqualTo("emotional")
            assertThat(Dimension.RATIONAL.dbValue()).isEqualTo("rational")
            assertThat(Dimension.AUTO.dbValue()).isEqualTo("auto")
        }

        @Test
        fun `from null 은 null`() {
            assertThat(Dimension.from(null)).isNull()
        }

        @Test
        fun `from 미지값 예외`() {
            assertThatThrownBy { Dimension.from("bogus") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("bogus")
        }
    }

    @Nested
    inner class FaithToneTest {
        @Test
        fun `dbValue 라운드트립`() {
            for (t in FaithTone.entries) {
                assertThat(FaithTone.from(t.dbValue())).isEqualTo(t)
            }
            assertThat(FaithTone.STRONG.dbValue()).isEqualTo("strong")
            assertThat(FaithTone.BALANCED.dbValue()).isEqualTo("balanced")
            assertThat(FaithTone.SOFT.dbValue()).isEqualTo("soft")
        }

        @Test
        fun `from null 은 BALANCED`() {
            assertThat(FaithTone.from(null)).isEqualTo(FaithTone.BALANCED)
        }

        @Test
        fun `from 미지값 은 BALANCED fallback`() {
            assertThat(FaithTone.from("nonsense")).isEqualTo(FaithTone.BALANCED)
        }
    }

    @Nested
    inner class HapticIntensityTest {
        @Test
        fun `dbValue 라운드트립`() {
            for (h in HapticIntensity.entries) {
                assertThat(HapticIntensity.from(h.dbValue())).isEqualTo(h)
            }
            assertThat(HapticIntensity.OFF.dbValue()).isEqualTo("off")
            assertThat(HapticIntensity.LOW.dbValue()).isEqualTo("low")
            assertThat(HapticIntensity.MEDIUM.dbValue()).isEqualTo("medium")
            assertThat(HapticIntensity.HIGH.dbValue()).isEqualTo("high")
        }

        @Test
        fun `from null 은 MEDIUM`() {
            assertThat(HapticIntensity.from(null)).isEqualTo(HapticIntensity.MEDIUM)
        }

        @Test
        fun `from 미지값 은 MEDIUM fallback`() {
            assertThat(HapticIntensity.from("loud")).isEqualTo(HapticIntensity.MEDIUM)
        }
    }

    @Nested
    inner class UserTypeTest {
        @Test
        fun `dbValue 라운드트립`() {
            for (t in UserType.entries) {
                assertThat(UserType.from(t.dbValue())).isEqualTo(t)
            }
            assertThat(UserType.GUEST.dbValue()).isEqualTo("guest")
            assertThat(UserType.OAUTH_GOOGLE.dbValue()).isEqualTo("oauth_google")
            assertThat(UserType.OAUTH_KAKAO.dbValue()).isEqualTo("oauth_kakao")
        }

        @Test
        fun `from null 은 GUEST`() {
            assertThat(UserType.from(null)).isEqualTo(UserType.GUEST)
        }

        @Test
        fun `from 미지값 예외`() {
            assertThatThrownBy { UserType.from("oauth_naver") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("oauth_naver")
        }
    }
}
