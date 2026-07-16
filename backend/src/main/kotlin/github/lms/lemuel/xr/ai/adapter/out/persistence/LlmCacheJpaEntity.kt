package github.lms.lemuel.xr.ai.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** llm_cache (V1 + V9 보강) — 응답 영구 캐시. */
@Entity
@Table(name = "llm_cache")
class LlmCacheJpaEntity {

    @Id
    @Column(name = "cache_key", length = 255)
    var cacheKey: String? = null

    @Column(name = "response", columnDefinition = "TEXT", nullable = false)
    var response: String? = null

    @Column(name = "provider", length = 20)
    var provider: String? = null

    @Column(name = "model", length = 50)
    var model: String? = null

    @Column(name = "purpose", length = 30)
    var purpose: String? = null

    @Column(name = "prompt_tokens")
    var promptTokens: Int? = null

    @Column(name = "completion_tokens")
    var completionTokens: Int? = null

    @Column(name = "hit_count", nullable = false)
    var hitCount: Int = 0

    @Column(name = "last_hit_at")
    var lastHitAt: LocalDateTime? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
}
