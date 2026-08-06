package github.lms.lemuel.xr.safety.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.regex.Pattern

/**
 * 위기 키워드 regex 스캐너. application.yml `safety.crisis-keywords-regex` 사용.
 *
 * 매칭 결과:
 * - matchedPattern: regex 의 패턴 키 (suicide_intent / self_harm 등)
 * - severity: medium/high/critical — 등급의 정본은 docs/EMOTION-CLASSIFIER.md §3 이다
 * - excerptHash: 매칭 문맥의 SHA-256 (평문 저장 금지 — ETHICS-LEGAL §3)
 *
 * **등급은 사용자의 위중도가 아니라 키워드의 정밀도를 잰다.** `자살` 은 다른 뜻으로 쓰일 여지가
 * 거의 없어 critical 이고, `마지막` 은 일상어와 구분이 안 돼 medium 이다. 등급을 내리는 것은
 * "덜 위험한 사람" 이라는 판단이 아니라 "이 문자열만으로는 확신할 수 없다" 는 고백이다.
 * 그래서 낮은 등급도 자원을 *숨기지는* 않는다 — 흐름을 끊지 않을 뿐 카드는 계속 보인다.
 */
@Component
class CrisisKeywordScanner(
    @param:Value("\${safety.crisis-keywords-regex}") regex: String,
) {

    /**
     * CASE_INSENSITIVE 인 이유: 사전에 영문 키워드(`suicide`, `kill myself`)가 있고
     * 사용자는 문장 첫머리에 대문자로 쓴다. 한글은 대소문자가 없어 영향이 없다.
     * 플래그를 정규식 리터럴(`(?i)`)이 아니라 컴파일 옵션으로 둔 것은 환경변수 override
     * (`SAFETY_CRISIS_REGEX`)로 들어온 식에도 똑같이 걸리게 하기 위해서다.
     */
    private val pattern: Pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)

    fun scan(text: String?): ScanResult {
        if (text.isNullOrBlank()) return ScanResult.none()

        val best = mostSevereMatch(text) ?: return ScanResult.none()

        // 매칭 문맥 ±20자
        val start = maxOf(0, best.start - 20)
        val end = minOf(text.length, best.end + 20)
        val hash = sha256Hex(text.substring(start, end))

        return ScanResult(true, best.pattern, severityOf(best.pattern), hash)
    }

    /**
     * 텍스트 안의 *모든* 매칭을 훑어 **가장 심각한** 것을 판정으로 삼는다. 없으면 null.
     *
     * 첫 매칭에서 멈추면 안 되는 이유 — 등급이 갈리는 순간 첫 매칭은 곧 *가장 앞의* 매칭일 뿐
     * 가장 위험한 매칭이 아니다. "마지막으로 정리하고 있어요. 자살할 생각입니다" 는 medium
     * 토큰(`마지막`)이 먼저 등장하므로, 첫 매칭만 보면 `자살` 이 있는데도 조용한 카드로 끝난다.
     * 등급이 전부 high 였던 시절에는 드러나지 않던 구멍이라 3단 등급과 함께 반드시 고쳐야 한다.
     *
     * critical 을 만나면 즉시 멈춘다 — 더 올라갈 등급이 없다.
     */
    private fun mostSevereMatch(text: String): Match? {
        val m = pattern.matcher(text)
        var best: Match? = null
        while (m.find()) {
            val p = classify(m)
            val rank = rankOf(severityOf(p))
            if (rank > (best?.rank ?: 0)) {
                best = Match(p, rank, m.start(), m.end())
                if (rank == MAX_RANK) break
            }
        }
        return best
    }

    private data class Match(val pattern: String, val rank: Int, val start: Int, val end: Int)

    /**
     * 호출마다 새 [MessageDigest] 를 만든다.
     *
     * 이전 구현은 인스턴스 필드 하나를 재사용했는데, 이 클래스는 싱글턴 `@Component` 이고
     * `scan` 은 요청 스레드에서 동시에 불린다. `MessageDigest` 는 상태를 가진 객체라
     * 동시 호출이 서로의 버퍼를 덮어써 *엉뚱한 해시* 가 나올 수 있었다. 해시는 평문을 저장하지
     * 않고 사후 추적하는 유일한 수단(ETHICS-LEGAL §3)이라, 틀린 해시는 감사 흔적의 소실이다.
     */
    private fun sha256Hex(s: String): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(StandardCharsets.UTF_8)),
        )

    /**
     * 매칭된 *명명 그룹* 으로 분류한다.
     *
     * 이전 구현은 무엇이 걸리든 "suicide_intent" 를 반환했다. 자해가 걸려도 감사 로그에는
     * 자살 의도로 남았다는 뜻이고, 안전 도메인 데이터가 사실과 달랐다.
     *
     * 이름 없는 regex(기존 SAFETY_CRISIS_REGEX override 포함)는 그룹이 없으므로
     * `crisis_unclassified` 로 떨어진다. 분류를 모른다고 통과시키지는 않는다.
     */
    private fun classify(m: java.util.regex.Matcher): String =
        GROUP_TO_PATTERN.entries
            .firstOrNull { (group, _) -> runCatching { m.group(group) }.getOrNull() != null }
            ?.value
            ?: "crisis_unclassified"

    /**
     * severity 매핑. 정본은 docs/EMOTION-CLASSIFIER.md §3 이고, 양방향 일치는
     * `CrisisKeywordDocContractTest` 가 강제한다.
     *
     * 매핑에 없는 분류는 [CRITICAL] 로 간주한다 — `high` 가 아니다. 3단 등급 이전에는
     * *어떤 매칭이든* LLM 호출을 건너뛰고 위기 화면을 강제했으므로, 지금의 [CRITICAL] 이
     * 그때의 동작이다. 이름 없는 override regex(`SAFETY_CRISIS_REGEX`)를 쓰는 운영자가
     * 등급 도입만으로 조용히 보호를 잃지 않게 하려면 fallback 은 그 동작이어야 한다.
     */
    private fun severityOf(pattern: String): String = PATTERN_TO_SEVERITY[pattern] ?: CRITICAL

    private fun rankOf(severity: String): Int = SEVERITY_RANK[severity] ?: MAX_RANK

    companion object {
        const val CRITICAL = "critical"
        const val HIGH = "high"
        const val MEDIUM = "medium"

        /** 정규식 명명 그룹 → 분류 키. 선언 순서가 곧 판정 우선순위다(심각도 내림차순). */
        val GROUP_TO_PATTERN = linkedMapOf(
            "suicideIntent" to "suicide_intent",
            "selfHarm" to "self_harm",
            "bereavementCompanionDeath" to "bereavement_companion_death",
            "despairIdeation" to "despair_ideation",
            "bereavementLonging" to "bereavement_longing",
            "riskSignal" to "risk_signal",
        )

        /**
         * 분류 키 → 등급.
         *
         * 사별 4종(2026-08-06 사용자 결정)은 두 분류로 갈라 넣었다. `같이 묻히` 는 동반 매장
         * 의사라 [HIGH], `곁으로 가고 싶`·`(고인) 곁에 있고 싶` 은 애도 표현과 구분이 안 돼
         * [MEDIUM] 이다. `나도 따라 죽고 싶` 은 별도 분류를 두지 않았다 — `죽고 싶`·`따라 죽`
         * 이 이미 `suicide_intent` 라 [CRITICAL] 로 잡힌다.
         */
        val PATTERN_TO_SEVERITY = mapOf(
            "suicide_intent" to CRITICAL,
            "self_harm" to CRITICAL,
            "bereavement_companion_death" to HIGH,
            "despair_ideation" to HIGH,
            "bereavement_longing" to MEDIUM,
            "risk_signal" to MEDIUM,
            "crisis_unclassified" to CRITICAL,
        )

        private val SEVERITY_RANK = mapOf(MEDIUM to 1, HIGH to 2, CRITICAL to 3)
        private const val MAX_RANK = 3
    }

    data class ScanResult(
        val matched: Boolean,
        val matchedPattern: String?,
        val severity: String?,
        val excerptHash: String?,
    ) {
        companion object {
            fun none(): ScanResult = ScanResult(false, null, null, null)
        }
    }
}
