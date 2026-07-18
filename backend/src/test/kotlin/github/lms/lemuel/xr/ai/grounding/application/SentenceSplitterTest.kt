package github.lms.lemuel.xr.ai.grounding.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SentenceSplitterTest {

    @Test
    fun `splits korean sentences on period and newline`() {
        val text = "하나님은 신실하시다. 그가 너를 붙드신다.\n두려워 말라"
        assertThat(SentenceSplitter.split(text))
            .containsExactly("하나님은 신실하시다.", "그가 너를 붙드신다.", "두려워 말라")
    }

    @Test
    fun `trims and drops blank segments`() {
        assertThat(SentenceSplitter.split("  첫 문장.   \n\n  둘째 문장!  "))
            .containsExactly("첫 문장.", "둘째 문장!")
    }

    @Test
    fun `empty or whitespace text yields empty list`() {
        assertThat(SentenceSplitter.split("   \n  ")).isEmpty()
        assertThat(SentenceSplitter.split("")).isEmpty()
    }

    @Test
    fun `single sentence without trailing punctuation is kept`() {
        assertThat(SentenceSplitter.split("근거 없는 한 문장")).containsExactly("근거 없는 한 문장")
    }
}
