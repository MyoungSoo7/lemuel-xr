package github.lms.lemuel.xr.safety.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrisisKeywordScannerTest {

    private val scanner = CrisisKeywordScanner(
        "(자살|자해|죽고\\s?싶|죽어\\s?버|뛰어내리)",
    )

    @Test
    fun `일반 텍스트는 매칭 X`() {
        val r = scanner.scan("오늘 너무 지쳤어요")
        assertThat(r.matched).isFalse()
    }

    @Test
    fun `자살 키워드 매칭`() {
        val r = scanner.scan("자살하고 싶어요")
        assertThat(r.matched).isTrue()
        // 이 fixture 는 이름 없는 regex 라 crisis_unclassified → critical(가장 보수적).
        assertThat(r.severity).isEqualTo("critical")
    }

    @Test
    fun `자해 변형도 매칭`() {
        val r = scanner.scan("그냥 죽어 버리고 싶어")
        assertThat(r.matched).isTrue()
    }

    @Test
    fun `excerpt hash 는 64자 hex`() {
        val r = scanner.scan("자살 생각이 들어요")
        assertThat(r.excerptHash).hasSize(64)
        assertThat(r.excerptHash).matches("[0-9a-f]+")
    }

    @Test
    fun `빈 문자열은 무사`() {
        val r = scanner.scan("")
        assertThat(r.matched).isFalse()

        val r2 = scanner.scan(null)
        assertThat(r2.matched).isFalse()
    }
}
