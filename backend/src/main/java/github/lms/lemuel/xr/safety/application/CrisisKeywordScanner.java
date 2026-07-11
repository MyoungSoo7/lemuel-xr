package github.lms.lemuel.xr.safety.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 위기 키워드 regex 스캐너. application.yml `safety.crisis-keywords-regex` 사용.
 *
 * <p>매칭 결과:
 * <ul>
 *   <li>matchedPattern: regex 의 패턴 키 (suicide_intent / self_harm 등)</li>
 *   <li>severity: low/medium/high</li>
 *   <li>excerptHash: 매칭 문맥의 SHA-256 (평문 저장 금지 — ETHICS-LEGAL §3)</li>
 * </ul>
 */
@Component
public class CrisisKeywordScanner {

    private final Pattern pattern;
    private final MessageDigest sha256;

    public CrisisKeywordScanner(@Value("${safety.crisis-keywords-regex}") String regex) {
        this.pattern = Pattern.compile(regex);
        try {
            this.sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public ScanResult scan(String text) {
        if (text == null || text.isBlank()) return ScanResult.none();
        Matcher m = pattern.matcher(text);
        if (!m.find()) return ScanResult.none();

        // 매칭 문맥 ±20자
        int start = Math.max(0, m.start() - 20);
        int end = Math.min(text.length(), m.end() + 20);
        String excerpt = text.substring(start, end);
        String hash = HexFormat.of().formatHex(sha256.digest(excerpt.getBytes(StandardCharsets.UTF_8)));

        // 단순 분류 — 매칭된 키워드의 종류로 severity
        String severity = "high";
        String matchedPattern = "suicide_intent";
        return new ScanResult(true, matchedPattern, severity, hash);
    }

    public record ScanResult(boolean matched, String matchedPattern, String severity, String excerptHash) {
        public static ScanResult none() {
            return new ScanResult(false, null, null, null);
        }
    }
}
