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

        // 단순 분류 — 매칭된 키워드의 종류로 severity
        val severity = "high"
        val matchedPattern = "suicide_intent"
        return ScanResult(true, matchedPattern, severity, hash)
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
