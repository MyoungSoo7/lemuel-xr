package github.lms.lemuel.xr.safety.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 가스라이팅 금지 토큰 스캐너.
 *
 * 토큰 목록의 출처는 저작 YAML 의 `safety_gates[].lint_forbidden_tokens` 다 — 트랙 B
 * `content/{인물}/scene*.yml` 과 Stage 1 `resources/scenarios/{인물}.yml` 두 곳.
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
 * - **토큰 바로 *앞* 의 문맥이 그 토큰의 정당한 용법을 가리키면 위반이 아니다.**
 *   [PRECEDING_EXEMPTIONS] 참고 — 뒤가 아니라 앞을 보는 면제라서 별도로 둔다.
 */
@Component
class ForbiddenTokenScanner(
    @param:Value("\${safety.forbidden-tokens.list:}") tokens: List<String>,
    // 축 예외 목록 — 여기 적히지 않은 토큰의 축은 [DEFAULT_AXIS] 다. 목록 전체를 축별로
    // 쪼개지 않는 이유: `list` 는 G5b·G5e·`newchar_gates.py` 가 파싱하는 단일 출처이고,
    // 축은 저작 yml 의 *게이트 id* 에 산다(런타임 토큰에 축을 새기지 않는다는 결정).
    // 그래서 축은 목록의 구조가 아니라 그 위에 얹는 **소수의 예외**로 표현한다.
    // 이 예외가 저작과 어긋나면 `SafetyMetricsGateBlockTest` 가 빨강을 낸다.
    // 기본값 `emptyList()` 는 *테스트 편의* 지 운영 여지가 아니다 — 이 목록이 통째로 비면
    // 모든 차단이 [DEFAULT_AXIS] 로 보고되는데, 그 조용한 축 붕괴는
    // `SafetyMetricsGateBlockTest` 가 실제 스프링 컨텍스트의 프로퍼티로 잡는다.
    @param:Value("\${safety.forbidden-tokens.theology-list:}") theologyTokens: List<String> = emptyList(),
) {

    /** 생성 시점에 한 번만 정규화 — 요청마다 반복하지 않는다. */
    private val normalizedTokens: List<Pair<String, String>> =
        tokens.filter { it.isNotBlank() }
            .map { it to normalize(it) }

    /** 정규화 형태로 담는다 — `scan` 이 비교하는 것도 정규화 형태다. */
    private val theologyAxisTokens: Set<String> =
        theologyTokens.filter { it.isNotBlank() }.map { normalize(it) }.toSet()

    fun scan(text: String?): ScanResult {
        if (text.isNullOrBlank() || normalizedTokens.isEmpty()) {
            return ScanResult(false, null)
        }
        val haystack = normalize(text)

        var hitToken: String? = null
        var hitNormalized: String? = null
        var hitAt = Int.MAX_VALUE
        for ((original, normalized) in normalizedTokens) {
            val at = firstNonExemptIndex(haystack, normalized)
            if (at in 0 until hitAt) {
                hitAt = at
                hitToken = original
                hitNormalized = normalized
            }
        }

        if (hitToken == null) return ScanResult(false, null)
        return ScanResult(
            matched = true,
            matchedToken = hitToken,
            axis = if (hitNormalized in theologyAxisTokens) THEOLOGY_AXIS else DEFAULT_AXIS,
            ruleId = ruleIdOf(hitToken),
        )
    }

    /**
     * `token` 의 출현 중 어떤 면제에도 걸리지 *않는* 가장 이른 위치. 없으면 -1.
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
            val exempt = isConcessiveNegation(haystack, at + token.length) ||
                isPrecedingExempt(token, haystack, at)
            if (!exempt) return at
            from = at + 1
        }
        return -1
    }

    /** 이 출현 바로 *앞* 이 [PRECEDING_EXEMPTIONS] 의 정당 문맥인가. 규칙 없는 토큰은 항상 false. */
    private fun isPrecedingExempt(token: String, haystack: String, at: Int): Boolean {
        val rule = PRECEDING_EXEMPTIONS[token] ?: return false
        return rule.containsMatchIn(haystack.substring(maxOf(0, at - LOOKBEHIND), at))
    }

    /** `from` 위치에서 곧바로 양보 부정 어미가 시작하는가. */
    private fun isConcessiveNegation(haystack: String, from: Int): Boolean {
        if (from >= haystack.length) return false
        val window = haystack.substring(from, minOf(from + LOOKAHEAD, haystack.length))
        return CONCESSIVE_NEGATION.containsMatchIn(window)
    }

    private fun normalize(s: String): String = s.trim().replace(WHITESPACE, " ")

    /**
     * @param axis 걸린 토큰이 속한 안전 축. 메트릭 차원으로 나간다 — 운영자는 신학 차단과
     *   정신건강 차단을 구분해야 하고, **사용자는 구분하지 못해야 한다**(seed AC2).
     * @param ruleId 토큰의 **불투명 식별자**. 메트릭 태그에 자구를 실으면 금칙 문구가
     *   대시보드·시계열 저장소로 새어 나간다 — 그 문구들은 가스라이팅 표현 그 자체다.
     *   자구는 로그(`ForbiddenTokenSanitizer`)에만 남고 메트릭에는 이 값만 나간다.
     */
    data class ScanResult(
        val matched: Boolean,
        val matchedToken: String?,
        val axis: String? = null,
        val ruleId: String? = null,
    )

    companion object {
        /** 신학 정통성 축. 저작 yml 의 `T1_` 접두 게이트와 같은 이름이어야 한다. */
        const val THEOLOGY_AXIS = "T1"

        /** 축 예외에 없는 토큰의 축. 목록의 절대다수가 여기 속한다. */
        const val DEFAULT_AXIS = "R2_R3"

        /** 메트릭에 실을 불투명 규칙 id. 토큰마다 안정적이고 자구를 복원할 수 없다. */
        fun ruleIdOf(token: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(StandardCharsets.UTF_8))
                .take(6)
                .joinToString("") { "%02x".format(it) }

        private val WHITESPACE = Regex("\\s+")

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
        private val CONCESSIVE_NEGATION = Regex("^[가-힣]{0,3}지\\s?(?:않|못)[가-힣]{0,3}도")
        private const val LOOKAHEAD = 12

        /**
         * **토큰별 선행 문맥 면제** — 토큰 바로 *앞* 이 정규식과 맞으면 그 출현은 위반이 아니다.
         *
         * [CONCESSIVE_NEGATION] 과 달리 (a) 뒤가 아니라 앞을 보고, (b) 목록 전 종이 아니라
         * **명시된 토큰에만** 적용된다. 전 종에 걸면 "하나님 덕분에 빨리 회복하세요" 같은 문장이
         * `빨리 회복` 까지 면제받는다 — 면제는 토큰 하나의 의미론이지 문장의 통행증이 아니다.
         *
         * `덕분에` (SR-2 마감 줄 평가 어휘): 금지 대상은 **사람에게 공을 돌리는 용법**이다.
         * "당신 덕분에 그 사람이 편히 갈 수 있었던 거예요" 는 사별한 사용자의 간병을 채점하고,
         * 뒤집으면 "덜 했으면 편히 못 갔다"가 된다. 그래서 사람 주어는 계속 막는다.
         * 주어가 생략된 형태("덕분에 편히 가셨어요")도 막는다 — 한국어에서 생략된 주어는
         * 청자, 즉 사용자다.
         *
         * 반면 **은혜의 주체는 신이다**(2026-08-05 사용자 결정: "덕분에는 신 덕분에고
         * 인간 덕분에는 굳이 필요 없다"). 이 시리즈의 주연이 삼위일체 신인 이상 신에게 공을
         * 돌리는 `덕분에` 는 축 그 자체이고, 금지 토큰 런타임은 **전역·무범위**라 이 면제가
         * 없으면 전 미션에서 그 문장을 못 쓴다.
         *
         * `그분` 은 넣지 않는다 — 사별 미션에서 "그분 덕분에" 는 고인을 가리킬 확률이 높다.
         * 신을 가리킨다는 것이 문자열만으로 확실한 호칭만 면제한다.
         */
        private val PRECEDING_EXEMPTIONS: Map<String, Regex> = mapOf(
            "덕분에" to Regex("(?:하나님|하느님|여호와|주님|성령|예수님|그리스도)(?:의)?\\s?(?:은혜\\s?)?$"),
        )

        /** 선행 문맥을 몇 자까지 거슬러 보는가. "하나님의 은혜 덕분에"(8자) 가 들어가는 최소치. */
        private const val LOOKBEHIND = 14
    }
}
