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
 * - severity: low/medium/high
 * - excerptHash: 매칭 문맥의 SHA-256 (평문 저장 금지 — ETHICS-LEGAL §3)
 */
@Component
class CrisisKeywordScanner(
    @param:Value("\${safety.crisis-keywords-regex}") regex: String,
) {

    private val pattern: Pattern = Pattern.compile(regex)
    private val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")

    fun scan(text: String?): ScanResult {
        if (text.isNullOrBlank()) return ScanResult.none()
        val m = pattern.matcher(text)
        if (!m.find()) return ScanResult.none()

        // 매칭 문맥 ±20자
        val start = maxOf(0, m.start() - 20)
        val end = minOf(text.length, m.end() + 20)
        val excerpt = text.substring(start, end)
        val hash = HexFormat.of().formatHex(sha256.digest(excerpt.toByteArray(StandardCharsets.UTF_8)))

        val pattern = classify(m)
        return ScanResult(true, pattern, severityOf(pattern), hash)
    }

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
     * severity 매핑.
     *
     * 현재 등록된 키워드는 전부 고위험이라 모두 high 다. 분류를 정교하게 만든 것이
     * 완화를 뜻하지 않는다 — `alert-severity-threshold=medium` 기준으로 high 여야
     * 사용자에게 위기 자원이 노출되므로, 낮추는 순간 보호가 약해진다.
     *
     * 앞으로 저위험 키워드를 추가할 때 이 맵에 tier 를 명시한다. 매핑에 없는 분류는
     * 보수적으로 high 로 간주한다.
     */
    private fun severityOf(pattern: String): String = PATTERN_TO_SEVERITY[pattern] ?: "high"

    private companion object {
        /** 정규식 명명 그룹 → 분류 키. 선언 순서가 곧 판정 우선순위다. */
        val GROUP_TO_PATTERN = linkedMapOf(
            "suicideIntent" to "suicide_intent",
            "selfHarm" to "self_harm",
        )

        val PATTERN_TO_SEVERITY = mapOf(
            "suicide_intent" to "high",
            "self_harm" to "high",
            "crisis_unclassified" to "high",
        )
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
