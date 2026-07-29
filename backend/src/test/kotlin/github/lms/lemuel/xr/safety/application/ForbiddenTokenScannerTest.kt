package github.lms.lemuel.xr.safety.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 가스라이팅 금지 토큰 스캐너.
 *
 * 토큰 목록의 출처는 저작 YAML(`content/{인물}/scene*.yml`)의 `lint_forbidden_tokens` 다.
 * 그 목록은 지금까지 *아무 코드도 읽지 않았고*, 그래서 게이트가 아니라 주석이었다.
 * 이 스캐너가 그 목록을 런타임 판정으로 승격시킨다.
 *
 * 금지 이유는 신학이 아니라 정신건강이다 — "믿음이 부족해서", "빨리 회복해라",
 * "다시 일어나 싸워라" 류는 절망 상태의 사용자에게 *책임을 전가* 하고 회복을 압박한다.
 * lemuel-xr 은 예방 영적 교육이지 치료 도구가 아니므로, 이런 문장은 어떤 맥락에서도
 * 사용자에게 노출되면 안 된다.
 */
class ForbiddenTokenScannerTest {

    private val scanner = ForbiddenTokenScanner(
        listOf("믿음이 부족", "빨리 회복", "다시 일어나 싸워", "정신 차려"),
    )

    @Test
    fun `평범한 위로 문장은 통과한다`() {
        val r = scanner.scan("오늘 하루도 버텨낸 것만으로 충분합니다.")

        assertThat(r.matched).isFalse()
        assertThat(r.matchedToken).isNull()
    }

    @Test
    fun `믿음 부족 책임전가 문장을 잡아낸다`() {
        val r = scanner.scan("당신이 힘든 건 믿음이 부족해서입니다.")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("믿음이 부족")
    }

    @Test
    fun `회복 압박 문장을 잡아낸다`() {
        val r = scanner.scan("빨리 회복하셔야죠.")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("빨리 회복")
    }

    @Test
    fun `여러 토큰이 걸리면 첫 번째로 발견된 토큰을 보고한다`() {
        val r = scanner.scan("정신 차려요. 다시 일어나 싸워야 합니다.")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("정신 차려")
    }

    @Test
    fun `null 과 빈 문자열은 통과한다`() {
        assertThat(scanner.scan(null).matched).isFalse()
        assertThat(scanner.scan("").matched).isFalse()
        assertThat(scanner.scan("   ").matched).isFalse()
    }

    @Test
    fun `토큰 목록이 비면 아무것도 잡지 않는다`() {
        val empty = ForbiddenTokenScanner(emptyList())

        assertThat(empty.scan("믿음이 부족해서 그래요").matched).isFalse()
    }

    @Test
    fun `공백이 섞여도 잡아낸다`() {
        val r = scanner.scan("믿음이  부족한 탓이에요")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("믿음이 부족")
    }
}
