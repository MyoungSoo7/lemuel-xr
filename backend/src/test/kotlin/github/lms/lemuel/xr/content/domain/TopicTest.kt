package github.lms.lemuel.xr.content.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TopicTest {

    @Test
    fun `byId 정합`() {
        assertThat(Topic.byId(1)).isEqualTo(Topic.JOURNAL)
        assertThat(Topic.byId(7)).isEqualTo(Topic.FEAR)
    }

    @Test
    fun `잘못된 id 예외`() {
        assertThatThrownBy { Topic.byId(99) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `모든 topic 은 id 와 제목 가짐`() {
        for (t in Topic.entries) {
            assertThat(t.id).isBetween(1, 7)
            assertThat(t.title).isNotBlank()
        }
    }
}
