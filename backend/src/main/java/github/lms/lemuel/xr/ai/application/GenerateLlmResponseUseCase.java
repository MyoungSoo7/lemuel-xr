package github.lms.lemuel.xr.ai.application;

import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheJpaEntity;
import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheRepository;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 응답 생성 — 캐시 hit 우선, miss 시 사이드카 호출.
 * Game decide 의 Scene 4 realtime LLM 같은 곳에서 사용.
 */
@Service
@RequiredArgsConstructor
public class GenerateLlmResponseUseCase {

    private final LlmCacheRepository cache;
    private final AiSidecarClient sidecar;
    private final CacheKeyComputer keyer;

    @Transactional
    public Result execute(String purpose, String promptKey, Map<String, Object> variables) {
        String key = keyer.compute(promptKey, variables);
        var cached = cache.findById(key);
        if (cached.isPresent()) {
            LlmCacheJpaEntity e = cached.get();
            e.setHitCount(e.getHitCount() + 1);
            e.setLastHitAt(LocalDateTime.now());
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
        return new Result(fresh.text(), fresh.provider(), fresh.model(), false);
    }

    public record Result(String text, String provider, String model, boolean cached) {}
}
