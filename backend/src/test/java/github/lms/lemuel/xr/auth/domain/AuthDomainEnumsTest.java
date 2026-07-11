package github.lms.lemuel.xr.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * auth/domain 순수 enum 단위 테스트 — dbValue() round-trip + from() fallback/예외 분기.
 */
class AuthDomainEnumsTest {

    @Nested
    class DimensionTest {
        @Test
        void dbValue_라운드트립() {
            for (Dimension d : Dimension.values()) {
                assertThat(Dimension.from(d.dbValue())).isEqualTo(d);
            }
            assertThat(Dimension.SPIRITUAL.dbValue()).isEqualTo("spiritual");
            assertThat(Dimension.EMOTIONAL.dbValue()).isEqualTo("emotional");
            assertThat(Dimension.RATIONAL.dbValue()).isEqualTo("rational");
            assertThat(Dimension.AUTO.dbValue()).isEqualTo("auto");
        }

        @Test
        void from_null_은_null() {
            assertThat(Dimension.from(null)).isNull();
        }

        @Test
        void from_미지값_예외() {
            assertThatThrownBy(() -> Dimension.from("bogus"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bogus");
        }
    }

    @Nested
    class FaithToneTest {
        @Test
        void dbValue_라운드트립() {
            for (FaithTone t : FaithTone.values()) {
                assertThat(FaithTone.from(t.dbValue())).isEqualTo(t);
            }
            assertThat(FaithTone.STRONG.dbValue()).isEqualTo("strong");
            assertThat(FaithTone.BALANCED.dbValue()).isEqualTo("balanced");
            assertThat(FaithTone.SOFT.dbValue()).isEqualTo("soft");
        }

        @Test
        void from_null_은_BALANCED() {
            assertThat(FaithTone.from(null)).isEqualTo(FaithTone.BALANCED);
        }

        @Test
        void from_미지값_은_BALANCED_fallback() {
            assertThat(FaithTone.from("nonsense")).isEqualTo(FaithTone.BALANCED);
        }
    }

    @Nested
    class HapticIntensityTest {
        @Test
        void dbValue_라운드트립() {
            for (HapticIntensity h : HapticIntensity.values()) {
                assertThat(HapticIntensity.from(h.dbValue())).isEqualTo(h);
            }
            assertThat(HapticIntensity.OFF.dbValue()).isEqualTo("off");
            assertThat(HapticIntensity.LOW.dbValue()).isEqualTo("low");
            assertThat(HapticIntensity.MEDIUM.dbValue()).isEqualTo("medium");
            assertThat(HapticIntensity.HIGH.dbValue()).isEqualTo("high");
        }

        @Test
        void from_null_은_MEDIUM() {
            assertThat(HapticIntensity.from(null)).isEqualTo(HapticIntensity.MEDIUM);
        }

        @Test
        void from_미지값_은_MEDIUM_fallback() {
            assertThat(HapticIntensity.from("loud")).isEqualTo(HapticIntensity.MEDIUM);
        }
    }

    @Nested
    class UserTypeTest {
        @Test
        void dbValue_라운드트립() {
            for (UserType t : UserType.values()) {
                assertThat(UserType.from(t.dbValue())).isEqualTo(t);
            }
            assertThat(UserType.GUEST.dbValue()).isEqualTo("guest");
            assertThat(UserType.OAUTH_GOOGLE.dbValue()).isEqualTo("oauth_google");
            assertThat(UserType.OAUTH_KAKAO.dbValue()).isEqualTo("oauth_kakao");
        }

        @Test
        void from_null_은_GUEST() {
            assertThat(UserType.from(null)).isEqualTo(UserType.GUEST);
        }

        @Test
        void from_미지값_예외() {
            assertThatThrownBy(() -> UserType.from("oauth_naver"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("oauth_naver");
        }
    }
}
