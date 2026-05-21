package github.lms.lemuel.xr.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import github.lms.lemuel.xr.common.AppException;
import org.junit.jupiter.api.Test;

class CharacterTest {

    @Test
    void lowercase_dbValue_파싱() {
        assertThat(Character.from("job")).isEqualTo(Character.JOB);
        assertThat(Character.from("elijah")).isEqualTo(Character.ELIJAH);
        assertThat(Character.from("joseph")).isEqualTo(Character.JOSEPH);
        assertThat(Character.from("moses")).isEqualTo(Character.MOSES);
        assertThat(Character.from("david")).isEqualTo(Character.DAVID);
        assertThat(Character.from("jesus")).isEqualTo(Character.JESUS);
    }

    @Test
    void uppercase_enum_이름_파싱() {
        assertThat(Character.from("JOSEPH")).isEqualTo(Character.JOSEPH);
    }

    @Test
    void 알수없는_값_E_CHARACTER_UNKNOWN() {
        assertThatThrownBy(() -> Character.from("simeon"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void null_E_CHARACTER_UNKNOWN() {
        assertThatThrownBy(() -> Character.from(null))
                .isInstanceOf(AppException.class);
    }
}
