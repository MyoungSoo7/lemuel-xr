package github.lms.lemuel.xr.tts.application

import github.lms.lemuel.xr.tts.application.port.out.TtsCachePort
import github.lms.lemuel.xr.tts.application.port.out.TtsSynthesisPort
import github.lms.lemuel.xr.tts.domain.TtsCache
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.HexFormat

@Service
class SynthesizeTtsUseCase(
    private val cache: TtsCachePort,
    private val sidecar: TtsSynthesisPort,
) {

    @Transactional
    fun execute(text: String, voiceId: String?, speakingRate: Double?): Result {
        val key = key(text, voiceId, speakingRate)
        val cached = cache.findById(key)
        if (cached.isPresent) {
            val hit = cached.get().registerHit(LocalDateTime.now())
            cache.save(hit)
            return Result(hit.audioUrl, hit.durationMs, true)
        }
        val fresh = sidecar.synthesize(text, voiceId, speakingRate)
        val entry = TtsCache.freshEntry(
            key, voiceId, fresh.engine, fresh.audioUrl, fresh.durationMs,
            LocalDateTime.now(),
        )
        cache.save(entry)
        return Result(fresh.audioUrl, fresh.durationMs, false)
    }

    private fun key(text: String, voiceId: String?, rate: Double?): String {
        val concat = text + "|" + (voiceId ?: "") +
            "|" + (rate?.toString() ?: "1.0")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(concat.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }

    data class Result(val audioUrl: String?, val durationMs: Int?, val cached: Boolean)
}
