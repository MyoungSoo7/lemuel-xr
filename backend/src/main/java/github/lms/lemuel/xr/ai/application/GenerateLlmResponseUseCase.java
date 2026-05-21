package github.lms.lemuel.xr.ai.application;

import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheJpaEntity;
import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    /**
     * AI 사이드카 호출은 outer transaction (예: DecideSceneUseCase) 의 rollback 신호를
     * 오염시키지 않도록 REQUIRES_NEW 로 격리. 사이드카 5xx 발생 시 *이 transaction 만*
     * rollback 되고 outer 는 그 RuntimeException 을 catch 해 정적 fallback 으로 계속 진행 가능.
     *
     * <p>주의: REQUIRES_NEW 는 Hikari 에서 *별도 connection* 을 잡음. 풀 사이즈가
     * AI 호출 동시성 * 2 이상이어야 starvation 회피.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
