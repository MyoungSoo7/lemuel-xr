package github.lms.lemuel.xr.tts.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** tts_cache (V1 + V9 보강). cache_key = sha256(text+voice+rate). */
@Entity
@Table(name = "tts_cache")
class TtsCacheJpaEntity {

    @Id
    @Column(name = "cache_key", length = 255)
    var cacheKey: String? = null

    @Column(name = "voice_id", length = 50)
    var voiceId: String? = null

    @Column(name = "engine", length = 20)
    var engine: String? = null

    @Column(name = "audio_url", columnDefinition = "TEXT")
    var audioUrl: String? = null

    @Column(name = "duration_ms")
    var durationMs: Int? = null

    @Column(name = "hit_count", nullable = false)
    var hitCount: Int = 0

    @Column(name = "last_hit_at")
    var lastHitAt: LocalDateTime? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
}
