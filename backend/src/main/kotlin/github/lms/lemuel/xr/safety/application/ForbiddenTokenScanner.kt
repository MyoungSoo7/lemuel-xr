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

        val firstHit = normalizedTokens
            .map { (original, normalized) -> original to haystack.indexOf(normalized) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
            ?: return ScanResult(false, null)

        return ScanResult(true, firstHit.first)
    }

    private fun normalize(s: String): String = s.trim().replace(WHITESPACE, " ")

    data class ScanResult(
        val matched: Boolean,
        val matchedToken: String?,
    )

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
