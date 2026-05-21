package github.lms.lemuel.xr.tts.application;

import github.lms.lemuel.xr.tts.adapter.out.persistence.TtsCacheJpaEntity;
import github.lms.lemuel.xr.tts.adapter.out.persistence.TtsCacheRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SynthesizeTtsUseCase {

    private final TtsCacheRepository cache;
    private final TtsSidecarClient sidecar;

    @Transactional
    public Result execute(String text, String voiceId, Double speakingRate) {
        String key = key(text, voiceId, speakingRate);
        var cached = cache.findById(key);
        if (cached.isPresent()) {
            var c = cached.get();
            c.setHitCount(c.getHitCount() + 1);
            c.setLastHitAt(LocalDateTime.now());
            return new Result(c.getAudioUrl(), c.getDurationMs(), true);
        }
        var fresh = sidecar.synthesize(text, voiceId, speakingRate);
        TtsCacheJpaEntity e = new TtsCacheJpaEntity();
        e.setCacheKey(key);
        e.setVoiceId(voiceId);
        e.setEngine(fresh.engine());
        e.setAudioUrl(fresh.audioUrl());
        e.setDurationMs(fresh.durationMs());
        e.setHitCount(0);
        e.setCreatedAt(LocalDateTime.now());
        cache.save(e);
        return new Result(fresh.audioUrl(), fresh.durationMs(), false);
    }

    private String key(String text, String voiceId, Double rate) {
        String concat = text + "|" + (voiceId == null ? "" : voiceId)
                + "|" + (rate == null ? "1.0" : rate.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(concat.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Result(String audioUrl, Integer durationMs, boolean cached) {}
}
