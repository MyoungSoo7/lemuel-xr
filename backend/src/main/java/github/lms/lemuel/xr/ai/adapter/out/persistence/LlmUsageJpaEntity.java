package github.lms.lemuel.xr.ai.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "llm_usage")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LlmUsageJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit = false;

    @Column(name = "cost_usd", precision = 8, scale = 5)
    private BigDecimal costUsd;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(name = "error_code", length = 50)
    private String errorCode;
}
