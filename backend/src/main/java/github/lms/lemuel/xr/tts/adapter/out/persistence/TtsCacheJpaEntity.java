package github.lms.lemuel.xr.tts.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** tts_cache (V1 + V9 보강). cache_key = sha256(text+voice+rate). */
@Entity
@Table(name = "tts_cache")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class TtsCacheJpaEntity {

    @Id
    @Column(name = "cache_key", length = 255)
    private String cacheKey;

    @Column(name = "voice_id", length = 50)
    private String voiceId;

    @Column(name = "engine", length = 20)
    private String engine;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @Column(name = "last_hit_at")
    private LocalDateTime lastHitAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
