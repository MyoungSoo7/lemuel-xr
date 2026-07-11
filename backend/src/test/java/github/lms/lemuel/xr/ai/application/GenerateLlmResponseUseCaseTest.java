package github.lms.lemuel.xr.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheJpaEntity;
import github.lms.lemuel.xr.ai.adapter.out.persistence.LlmCacheRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * GenerateLlmResponseUseCase 단위 테스트 — 캐시 hit / miss / 생성 비활성화 분기를 Mockito 로 커버.
 * 실제 사이드카/DB 호출 없음. 진짜 {@link CacheKeyComputer} + {@link SimpleMeterRegistry} 사용.
 */
class GenerateLlmResponseUseCaseTest {

    private final CacheKeyComputer keyer = new CacheKeyComputer();
    private final MeterRegistry meter = new SimpleMeterRegistry();

    private GenerateLlmResponseUseCase uc(LlmCacheRepository cache, AiSidecarClient sidecar,
                                          boolean enabled, String fallback) {
        return new GenerateLlmResponseUseCase(cache, sidecar, keyer, meter, enabled, fallback);
    }

    private LlmCacheJpaEntity cachedEntity(String key, String response) {
        LlmCacheJpaEntity e = new LlmCacheJpaEntity();
        e.setCacheKey(key);
        e.setResponse(response);
        e.setProvider("anthropic");
        e.setModel("claude-x");
        e.setHitCount(3);
        return e;
    }

    // ───────────────────────── 생성 비활성화 (enabled=false) ─────────────────────────

    @Test
    void 비활성화_사전캐시_있으면_캐시응답_반환() {
        LlmCacheRepository cache = Mockito.mock(LlmCacheRepository.class);
        AiSidecarClient sidecar = Mockito.mock(AiSidecarClient.class);
        String key = keyer.compute("k", Map.of("a", 1));
        when(cache.findById(key)).thenReturn(Optional.of(cachedEntity(key, "사전캐시 응답")));

        var r = uc(cache, sidecar, false, "정적 fallback").execute("meditation", "k", Map.of("a", 1));

        assertThat(r.text()).isEqualTo("사전캐시 응답");
        assertThat(r.cached()).isTrue();
        assertThat(r.provider()).isEqualTo("anthropic");
        verify(sidecar, never()).generate(any(), any(), any());
    }

    @Test
    void 비활성화_사전캐시_없으면_정적_fallback() {
        LlmCacheRepository cache = Mockito.mock(LlmCacheRepository.class);
        AiSidecarClient sidecar = Mockito.mock(AiSidecarClient.class);
        when(cache.findById(anyString())).thenReturn(Optional.empty());

        var r = uc(cache, sidecar, false, "정적 fallback 텍스트").execute("meditation", "k", Map.of());

        assertThat(r.text()).isEqualTo("정적 fallback 텍스트");
        assertThat(r.provider()).isEqualTo("static");
        assertThat(r.model()).isEqualTo("fallback");
        assertThat(r.cached()).isFalse();
        verify(sidecar, never()).generate(any(), any(), any());
        assertThat(meter.get("llm.generation.disabled").tag("purpose", "meditation").counter().count())
                .isEqualTo(1.0);
    }

    // ───────────────────────── 생성 활성화 (enabled=true) ─────────────────────────

    @Test
    void 활성화_캐시_hit_이면_hitCount_증가_사이드카_미호출() {
        LlmCacheRepository cache = Mockito.mock(LlmCacheRepository.class);
        AiSidecarClient sidecar = Mockito.mock(AiSidecarClient.class);
        String key = keyer.compute("k", Map.of("x", "y"));
        LlmCacheJpaEntity hit = cachedEntity(key, "캐시된 본문");
        when(cache.findById(key)).thenReturn(Optional.of(hit));

        var r = uc(cache, sidecar, true, "").execute("meditation", "k", Map.of("x", "y"));

        assertThat(r.text()).isEqualTo("캐시된 본문");
        assertThat(r.cached()).isTrue();
        assertThat(hit.getHitCount()).isEqualTo(4);          // 3 → 4
        assertThat(hit.getLastHitAt()).isNotNull();
        verify(sidecar, never()).generate(any(), any(), any());
        assertThat(meter.get("llm.cache.hit").tag("purpose", "meditation").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void 활성화_캐시_miss_이면_사이드카_호출_후_저장() {
        LlmCacheRepository cache = Mockito.mock(LlmCacheRepository.class);
        AiSidecarClient sidecar = Mockito.mock(AiSidecarClient.class);
        when(cache.findById(anyString())).thenReturn(Optional.empty());
        when(sidecar.generate(any(), any(), any())).thenReturn(
                new AiSidecarClient.GenerateResult("새 본문", "anthropic", "claude-x", 10, 20, false));

        var r = uc(cache, sidecar, true, "").execute("meditation", "k", Map.of("a", 1));

        assertThat(r.text()).isEqualTo("새 본문");
        assertThat(r.cached()).isFalse();
        assertThat(r.provider()).isEqualTo("anthropic");

        ArgumentCaptor<LlmCacheJpaEntity> saved = ArgumentCaptor.forClass(LlmCacheJpaEntity.class);
        verify(cache).save(saved.capture());
        LlmCacheJpaEntity e = saved.getValue();
        assertThat(e.getResponse()).isEqualTo("새 본문");
        assertThat(e.getProvider()).isEqualTo("anthropic");
        assertThat(e.getPurpose()).isEqualTo("meditation");
        assertThat(e.getPromptTokens()).isEqualTo(10);
        assertThat(e.getCompletionTokens()).isEqualTo(20);
        assertThat(e.getHitCount()).isZero();
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(meter.get("llm.cache.miss").tag("provider", "anthropic").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void 활성화_miss_provider_null_이면_메트릭_태그_unknown() {
        LlmCacheRepository cache = Mockito.mock(LlmCacheRepository.class);
        AiSidecarClient sidecar = Mockito.mock(AiSidecarClient.class);
        when(cache.findById(anyString())).thenReturn(Optional.empty());
        when(sidecar.generate(any(), any(), any())).thenReturn(
                new AiSidecarClient.GenerateResult("본문", null, null, null, null, false));

        var r = uc(cache, sidecar, true, "").execute("scene", "k", Map.of());

        assertThat(r.provider()).isNull();
        assertThat(meter.get("llm.cache.miss").tag("provider", "unknown").counter().count())
                .isEqualTo(1.0);
    }
}
