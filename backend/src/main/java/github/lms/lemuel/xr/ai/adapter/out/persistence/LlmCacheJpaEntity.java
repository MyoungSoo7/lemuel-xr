package github.lms.lemuel.xr.ai.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** llm_cache (V1 + V9 보강) — 응답 영구 캐시. */
@Entity
@Table(name = "llm_cache")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LlmCacheJpaEntity {

    @Id
    @Column(name = "cache_key", length = 255)
    private String cacheKey;

    @Column(name = "response", columnDefinition = "TEXT", nullable = false)
    private String response;

    @Column(name = "provider", length = 20)
    private String provider;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "purpose", length = 30)
    private String purpose;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @Column(name = "last_hit_at")
    private LocalDateTime lastHitAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
