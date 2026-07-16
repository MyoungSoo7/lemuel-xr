package github.lms.lemuel.xr.game.domain

import github.lms.lemuel.xr.common.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CharacterTest {

    @Test
    fun `lowercase dbValue 파싱`() {
        assertThat(Character.from("job")).isEqualTo(Character.JOB)
        assertThat(Character.from("elijah")).isEqualTo(Character.ELIJAH)
        assertThat(Character.from("joseph")).isEqualTo(Character.JOSEPH)
        assertThat(Character.from("moses")).isEqualTo(Character.MOSES)
        assertThat(Character.from("david")).isEqualTo(Character.DAVID)
        assertThat(Character.from("jesus")).isEqualTo(Character.JESUS)
    }

    @Test
    fun `uppercase enum 이름 파싱`() {
        assertThat(Character.from("JOSEPH")).isEqualTo(Character.JOSEPH)
    }

    @Test
    fun `알수없는 값 E_CHARACTER_UNKNOWN`() {
        assertThatThrownBy { Character.from("simeon") }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `null E_CHARACTER_UNKNOWN`() {
        assertThatThrownBy { Character.from(null) }
            .isInstanceOf(AppException::class.java)
    }
}
