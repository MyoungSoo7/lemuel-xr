package github.lms.lemuel.xr.safety.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 가스라이팅 금지 토큰 스캐너.
 *
 * 토큰 목록의 출처는 저작 YAML(`content/{인물}/scene*.yml`)의 `lint_forbidden_tokens` 다.
 * 그 목록은 오랫동안 *어떤 코드도 읽지 않아* 게이트가 아니라 주석에 머물렀다.
 * 이 스캐너가 그것을 런타임 판정으로 승격시킨다.
 *
 * 금지 이유는 신학이 아니라 정신건강이다 — "믿음이 부족해서", "빨리 회복해라",
 * "다시 일어나 싸워라" 류는 절망 상태의 사용자에게 *책임을 전가* 하고 회복을 압박한다.
 * lemuel-xr 은 예방 영적 교육이지 치료 도구가 아니므로 어떤 맥락에서도 노출되면 안 된다.
 *
 * 판정 규칙:
 * - 공백은 정규화해서 비교한다. LLM 출력은 같은 표현도 공백 수가 들쭉날쭉해
 *   단순 `contains` 로는 "믿음이  부족"(두 칸)을 놓친다.
 * - 여러 토큰이 걸리면 *텍스트에서 가장 먼저 등장한* 토큰을 보고한다. 목록 순서가 아니라
 *   등장 위치 기준이어야 로그·감사에서 "어디서 걸렸는지"가 사람 직관과 맞는다.
 * - **양보 부정(`-지 않아도` / `-지 못해도`)이 토큰 바로 뒤에 붙으면 위반이 아니다.**
 *   [CONCESSIVE_NEGATION] 참고.
 */
@Component
class ForbiddenTokenScanner(
    @param:Value("\${safety.forbidden-tokens.list:}") tokens: List<String>,
) {

    /** 생성 시점에 한 번만 정규화 — 요청마다 반복하지 않는다. */
    private val normalizedTokens: List<Pair<String, String>> =
        tokens.filter { it.isNotBlank() }
            .map { it to normalize(it) }

    fun scan(text: String?): ScanResult {
        if (text.isNullOrBlank() || normalizedTokens.isEmpty()) {
            return ScanResult(false, null)
        }
        val haystack = normalize(text)

        var hitToken: String? = null
        var hitAt = Int.MAX_VALUE
        for ((original, normalized) in normalizedTokens) {
            val at = firstNonExemptIndex(haystack, normalized)
            if (at in 0 until hitAt) {
                hitAt = at
                hitToken = original
            }
        }

        return if (hitToken == null) ScanResult(false, null) else ScanResult(true, hitToken)
    }

    /**
     * `token` 의 출현 중 양보 부정으로 면제되지 *않는* 가장 이른 위치. 없으면 -1.
     *
     * 같은 토큰이 한 문장에 두 번 나오면 앞의 것만 면제되고 뒤의 것은 위반일 수 있다
     * ("빨리 회복하지 않아도 됩니다. 그래도 빨리 회복하세요." ← 뒤가 압박) — 그래서
     * 첫 출현에서 멈추지 않고 면제되지 않는 출현을 만날 때까지 이어서 찾는다.
     */
    private fun firstNonExemptIndex(haystack: String, token: String): Int {
        var from = 0
        while (from <= haystack.length - token.length) {
            val at = haystack.indexOf(token, from)
            if (at < 0) return -1
            if (!isConcessiveNegation(haystack, at + token.length)) return at
            from = at + 1
        }
        return -1
    }

    /** `from` 위치에서 곧바로 양보 부정 어미가 시작하는가. */
    private fun isConcessiveNegation(haystack: String, from: Int): Boolean {
        if (from >= haystack.length) return false
        val window = haystack.substring(from, minOf(from + LOOKAHEAD, haystack.length))
        return CONCESSIVE_NEGATION.containsMatchIn(window)
    }

    private fun normalize(s: String): String = s.trim().replace(WHITESPACE, " ")

    data class ScanResult(
        val matched: Boolean,
        val matchedToken: String?,
    )

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /**
         * 어간 뒤 **양보 부정** — `<어간 완성 0~3자><지> <않|못><0~3자><도>`.
         *
         * 한국어는 부정이 어간 *뒤* 에 `-지 않/못` 으로 붙는다. 그래서 어간에서 끊은 토큰
         * (`빨리 회복`, `믿음이 부족`, `다시 이겨`)은 그 축의 정당한 위로까지 반드시 삼킨다 —
         * "빨리 회복하지 않아도 됩니다" 는 R3 축이 *하려고 존재하는 말* 인데 `빨리 회복` 에 걸렸다.
         * 토큰을 명령형으로 좁히는 길도 있지만, 좁은 토큰이 활용만으로 뚫리는 건 이 저장소가
         * 이미 실측한 실패다(솔로몬 `이제 깨달았으니` → `깨달았으`). 그래서 토큰이 아니라
         * *형태론* 을 여기서 다룬다 — 한 번 고치면 목록 전 종에 적용된다(토큰 수는 계속 는다).
         *
         * **부정만으로 면제하지 않고 양보 어미 `-도` 까지 요구하는 이유**: `-지 않으면 안 된다`
         * 는 이중부정이라 뜻이 압박 그대로다("빨리 회복하지 않으면 안 됩니다"). `-도` 가 붙은
         * 형태(`않아도`·`않으셔도`·`못해도`)만이 허용·위로다.
         *
         * 면제하지 *않는* 것 — `-가 아니` 계열(`믿음이 약해서가 아닙니다`). 그건 부정형 위로가
         * 아니라 앱이 먼저 수치 프레임을 꺼내는 문장이라 의도적으로 막기로 한 결정이고,
         * `ForbiddenTokenConfigTest.의도적 과차단` 이 그 결정을 고정한다.
         *
         * 창을 12자로 자르는 이유: 부정이 어간에 *직접* 붙어야 한다. 문장이 넘어간 뒤의 부정
         * ("빨리 회복하세요. 참지 않아도 됩니다.")까지 면제하면 앞 문장의 압박이 풀린다.
         */
        val CONCESSIVE_NEGATION = Regex("^[가-힣]{0,3}지\\s?(?:않|못)[가-힣]{0,3}도")
        const val LOOKAHEAD = 12
    }
}
