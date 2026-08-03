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

    // ---------------------------------------------------------------------
    // 양보 부정 면제 — 한국어는 부정이 어간 뒤에 붙어서(-지 않/못) 어간에서 끊은
    // 토큰은 그 축의 정당한 위로까지 반드시 삼킨다. 아래가 그 경계 명세다.
    // ---------------------------------------------------------------------

    @Test
    fun `어간 뒤 양보 부정은 위반이 아니다 — 그 축이 하려는 말이다`() {
        // "빨리 회복하지 않아도 됩니다" 는 R3 축이 *존재하는 이유* 인 문장인데
        // 어간 토큰 '빨리 회복' 에 걸려 오랫동안 차단되고 있었다.
        val mustPass = listOf(
            "빨리 회복하지 않아도 됩니다.",
            "빨리 회복하지 않으셔도 괜찮습니다.",
            "빨리 회복되지 않아도 괜찮습니다.",
            "빨리 회복하지 못해도 괜찮습니다.",
            "믿음이 부족하지 않아도 괜찮습니다.",
            "믿음이  부족하지 않아도 괜찮습니다.", // 공백 정규화 뒤에도 면제가 살아 있는가
        )

        assertThat(mustPass.filter { scanner.scan(it).matched })
            .describedAs("양보 부정형 위로가 차단됐다 — 과차단은 안전이 아니라 손실이다")
            .isEmpty()
    }

    @Test
    fun `이중부정 압박은 면제하지 않는다`() {
        // '-지 않으면 안 된다' 는 부정이 두 번 겹쳐 뜻이 압박 그대로다.
        // 그래서 면제 조건은 '부정' 이 아니라 '부정 + 양보 어미 -도' 다.
        val r = scanner.scan("빨리 회복하지 않으면 안 됩니다.")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("빨리 회복")
    }

    @Test
    fun `문장이 넘어간 뒤의 부정은 앞 문장의 압박을 풀지 못한다`() {
        // 부정은 어간에 *직접* 붙어야 면제다. 뒤 문장의 부정까지 끌어오면
        // "빨리 회복하세요" 라는 압박이 통째로 새어 나간다.
        val r = scanner.scan("빨리 회복하세요. 억지로 참지 않아도 됩니다.")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("빨리 회복")
    }

    @Test
    fun `같은 토큰이 두 번 나오면 면제되지 않은 뒤쪽을 잡는다`() {
        // 첫 출현에서 멈추면 앞의 면제가 뒤의 위반을 가린다.
        val r = scanner.scan("빨리 회복하지 않아도 됩니다. 그래도 빨리 회복하세요.")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedToken).isEqualTo("빨리 회복")
    }

    @Test
    fun `알려진 한계 — 부정이 어간에서 떨어지면 여전히 과차단이다`() {
        // "빨리 회복해야 한다고 느끼지 않아도 됩니다" 는 뜻으로는 위로인데,
        // 부정이 다른 용언('느끼-')에 붙어 있어 표층 규칙으로는 갈라낼 수 없다.
        // 이 줄은 결함 기록이다 — 잡히지 않게 되면 여기서 빼고 위 mustPass 로 옮길 것.
        assertThat(scanner.scan("빨리 회복해야 한다고 느끼지 않아도 됩니다.").matched).isTrue()
    }
}
