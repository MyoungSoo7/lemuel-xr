package github.lms.lemuel.xr.ai.application

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.TreeMap

/** LlmCache.cache_key 생성 — promptKey + sorted variables 의 SHA-256. */
@Component
class CacheKeyComputer {

    fun compute(promptKey: String, variables: Map<String, Any?>?): String {
        val sorted = TreeMap<String, Any?>(variables ?: emptyMap())
        val concat = "$promptKey:$sorted"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(concat.toByteArray(StandardCharsets.UTF_8))
        return promptKey + ":" + HexFormat.of().formatHex(hash).substring(0, 32)
    }
}
