package github.lms.lemuel.xr.safety.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 금지 토큰 *집행* 규칙 — 무엇을 바꾸고 무엇을 그대로 두는지.
 *
 * 판정 규칙 자체는 [ForbiddenTokenScannerTest] 가 다룬다. 여기서는 순회·치환의 경계를 못 박는다:
 * 걸린 leaf 하나만 바뀌고, 키·비문자열·자료구조는 그대로여야 한다.
 */
class ForbiddenTokenSanitizerTest {

    private val fallback = "대체 문구"
    private val sanitizer = ForbiddenTokenSanitizer(ForbiddenTokenScanner(listOf("믿음이 부족")), fallback)

    @Test
    fun `걸리면 대체 문구, 아니면 원문`() {
        assertThat(sanitizer.sanitizeText("믿음이 부족해서요", "t")).isEqualTo(fallback)
        assertThat(sanitizer.sanitizeText("괜찮습니다", "t")).isEqualTo("괜찮습니다")
    }

    @Test
    fun `중첩 map 과 list 의 문자열 leaf 를 훑는다`() {
        val out = sanitizer.sanitize(
            mapOf(
                "a" to "믿음이 부족합니다",
                "b" to mapOf("c" to "믿음이  부족 합니다", "d" to "괜찮습니다"),
                "e" to listOf("믿음이 부족", "괜찮습니다"),
            ),
            "t",
        )

        assertThat(out["a"]).isEqualTo(fallback)
        assertThat(out["b"]).isEqualTo(mapOf("c" to fallback, "d" to "괜찮습니다"))
        assertThat(out["e"]).isEqualTo(listOf(fallback, "괜찮습니다"))
    }

    @Test
    fun `키는 건드리지 않는다`() {
        // 키가 대체 문구로 바뀌면 클라이언트가 찾는 자료구조가 통째로 사라진다.
        val out = sanitizer.sanitize(mapOf("믿음이 부족" to "괜찮습니다"), "t")

        assertThat(out).containsOnlyKeys("믿음이 부족")
        assertThat(out["믿음이 부족"]).isEqualTo("괜찮습니다")
    }

    @Test
    fun `비문자열 값은 타입까지 그대로 보존한다`() {
        val out = sanitizer.sanitize(mapOf("id" to 4, "flag" to true, "none" to null), "t")

        assertThat(out).isEqualTo(mapOf("id" to 4, "flag" to true, "none" to null))
    }

    @Test
    fun `깨끗한 payload 는 동등한 값으로 돌아온다`() {
        val input = mapOf("a" to "괜찮습니다", "b" to listOf(1, 2), "c" to mapOf("d" to "좋습니다"))

        assertThat(sanitizer.sanitize(input, "t")).isEqualTo(input)
    }

    @Test
    fun `토큰 목록이 비면 아무것도 바꾸지 않는다`() {
        val off = ForbiddenTokenSanitizer(ForbiddenTokenScanner(emptyList()), fallback)

        assertThat(off.sanitizeText("믿음이 부족해서요", "t")).isEqualTo("믿음이 부족해서요")
    }

    @Test
    fun `대체 문구가 비면 생성 자체가 실패한다`() {
        // 예전에는 `\${...:}` 기본값이 있어서, 설정이 빠지면 걸린 문장이 대체 문구가 아니라
        // **빈 문자열** 로 사용자에게 나갔다. 게이트는 "차단했다" 는 로그를 남기고, 화면에는
        // 아무 말도 남지 않는다. 절망 상태의 사용자에게 침묵은 안전 동작이 아니다.
        // 이 실패는 요청 시점이 아니라 *기동 시점* 에 나야 한다.
        listOf("", "   ").forEach { blank ->
            assertThatThrownBy { ForbiddenTokenSanitizer(ForbiddenTokenScanner(listOf("믿음이 부족")), blank) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("safety.forbidden-tokens.fallback-text")
        }
    }
}
