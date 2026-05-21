package github.lms.lemuel.xr.ai.application;

import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheJpaEntity;
import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 응답 생성 — 캐시 hit 우선, miss 시 사이드카 호출.
 * Game decide 의 Scene 4 realtime LLM 같은 곳에서 사용.
 *
 * <p>메트릭: {@code llm.cache.hit{purpose}} / {@code llm.cache.miss{purpose,provider}}.
 * Grafana 의 *AI 비용·캐시* row 에서 hit rate 차트 (목표 80%+).</p>
 */
@Service
public class GenerateLlmResponseUseCase {

    private final LlmCacheRepository cache;
    private final AiSidecarClient sidecar;
    private final CacheKeyComputer keyer;
    private final MeterRegistry meter;

    public GenerateLlmResponseUseCase(LlmCacheRepository cache,
                                       AiSidecarClient sidecar,
                                       CacheKeyComputer keyer,
                                       MeterRegistry meter) {
        this.cache = cache;
        this.sidecar = sidecar;
        this.keyer = keyer;
        this.meter = meter;
    }

    @Transactional
    public Result execute(String purpose, String promptKey, Map<String, Object> variables) {
        String key = keyer.compute(promptKey, variables);
        var cached = cache.findById(key);
        if (cached.isPresent()) {
            LlmCacheJpaEntity e = cached.get();
            e.setHitCount(e.getHitCount() + 1);
            e.setLastHitAt(LocalDateTime.now());
            Counter.builder("llm.cache.hit").tag("purpose", purpose).register(meter).increment();
            return new Result(e.getResponse(), e.getProvider(), e.getModel(), true);
        }
        var fresh = sidecar.generate(purpose, promptKey, variables);
        LlmCacheJpaEntity e = new LlmCacheJpaEntity();
        e.setCacheKey(key);
        e.setResponse(fresh.text());
        e.setProvider(fresh.provider());
        e.setModel(fresh.model());
        e.setPurpose(purpose);
        e.setPromptTokens(fresh.promptTokens());
        e.setCompletionTokens(fresh.completionTokens());
        e.setHitCount(0);
        e.setCreatedAt(LocalDateTime.now());
        cache.save(e);
        Counter.builder("llm.cache.miss")
                .tag("purpose", purpose)
                .tag("provider", fresh.provider() == null ? "unknown" : fresh.provider())
                .register(meter).increment();
        return new Result(fresh.text(), fresh.provider(), fresh.model(), false);
    }

    public record Result(String text, String provider, String model, boolean cached) {}
}
