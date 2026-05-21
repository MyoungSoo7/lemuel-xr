package github.lms.lemuel.xr.ai.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** LlmCache.cache_key 생성 — promptKey + sorted variables 의 SHA-256. */
@Component
public class CacheKeyComputer {

    public String compute(String promptKey, Map<String, Object> variables) {
        TreeMap<String, Object> sorted = new TreeMap<>(variables == null ? Map.of() : variables);
        String concat = promptKey + ":" + sorted.toString();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(concat.getBytes(StandardCharsets.UTF_8));
            return promptKey + ":" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
