package github.lms.lemuel.xr.ai.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "llm_usage")
class LlmUsageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: LocalDateTime? = null

    @Column(name = "user_id")
    var userId: UUID? = null

    @Column(nullable = false, length = 30)
    var purpose: String? = null

    @Column(nullable = false, length = 20)
    var provider: String? = null

    @Column(nullable = false, length = 50)
    var model: String? = null

    @Column(name = "prompt_tokens")
    var promptTokens: Int? = null

    @Column(name = "completion_tokens")
    var completionTokens: Int? = null

    @Column(name = "latency_ms")
    var latencyMs: Int? = null

    @Column(name = "cache_hit", nullable = false)
    var cacheHit: Boolean = false

    @Column(name = "cost_usd", precision = 8, scale = 5)
    var costUsd: BigDecimal? = null

    @Column(name = "request_id", length = 80)
    var requestId: String? = null

    @Column(nullable = false)
    var success: Boolean = true

    @Column(name = "error_code", length = 50)
    var errorCode: String? = null
}
