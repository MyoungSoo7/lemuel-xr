package github.lms.lemuel.xr.ai.application

import github.lms.lemuel.xr.ai.application.port.out.LlmCachePort
import github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort
import github.lms.lemuel.xr.ai.application.port.out.LlmMetricsPort
import github.lms.lemuel.xr.ai.domain.LlmCache
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * LLM 응답 생성 — 캐시 hit 우선, miss 시 사이드카 호출.
 * Game decide 의 Scene 4 realtime LLM 같은 곳에서 사용.
 *
 * 메트릭: `llm.cache.hit{purpose}` / `llm.cache.miss{purpose,provider}`.
 * Grafana 의 *AI 비용·캐시* row 에서 hit rate 차트 (목표 80%+). 메트릭 발행은
 * [LlmMetricsPort] 뒤로 격리 (DIP) — 유즈케이스는 Micrometer 를 모른다.
 *
 * 사이드카 호출도 [LlmGenerationPort] 뒤로 격리 (DIP) — 구체 HTTP 세부는
 * `ai/adapter/out/sidecar/LlmGenerationSidecarAdapter` 안에만 존재. 포트·유즈케이스는
 * JPA 엔티티도 알지 못하고 도메인 [LlmCache] 로만 대화한다.
 */
@Service
class GenerateLlmResponseUseCase(
    private val cache: LlmCachePort,
    private val sidecar: LlmGenerationPort,
    private val keyer: CacheKeyComputer,
    private val metrics: LlmMetricsPort,
    @Value("\${ai.generation.enabled:false}") private val generationEnabled: Boolean,
    @Value("\${ai.generation.disabled-fallback-text:}") private val disabledFallback: String,
    private val forbiddenTokenScanner: ForbiddenTokenScanner,
    @Value("\${safety.forbidden-tokens.fallback-text:}") private val forbiddenTokenFallback: String,
) {

    /**
     * AI 사이드카 호출은 outer transaction (예: DecideSceneUseCase) 의 rollback 신호를
     * 오염시키지 않도록 REQUIRES_NEW 로 격리. 사이드카 5xx 발생 시 *이 transaction 만*
     * rollback 되고 outer 는 그 RuntimeException 을 catch 해 정적 fallback 으로 계속 진행 가능.
     *
     * 주의: REQUIRES_NEW 는 Hikari 에서 *별도 connection* 을 잡음. 풀 사이즈가
     * AI 호출 동시성 * 2 이상이어야 starvation 회피.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun execute(purpose: String, promptKey: String, variables: Map<String, Any?>): Result {
        // 2026-05-22 결정: 임상 자문 영입 전까지 LLM 생성 비활성화.
        // 사전 캐시된 응답이 있으면 그것만 반환, 없으면 정적 fallback.
        if (!generationEnabled) {
            metrics.generationDisabled(purpose)
            val key0 = keyer.compute(promptKey, variables)
            val cached0 = cache.findByCacheKey(key0)
            if (cached0.isPresent) {
                val c = cached0.get()
                return Result(c.response, c.provider, c.model, true)
            }
            return Result(disabledFallback, "static", "fallback", false)
        }

        val key = keyer.compute(promptKey, variables)
        val cached = cache.findByCacheKey(key)
        if (cached.isPresent) {
            val hit = cached.get()
            // 게이트 도입 *이전* 에 캐시된 응답도 검사한다. 캐시를 통과시키면
            // 오염된 문장이 영구히 살아남아 게이트가 무력해진다.
            if (forbiddenTokenScanner.scan(hit.response).matched) {
                return Result(forbiddenTokenFallback, "static", "safety-fallback", false)
            }
            cache.save(hit.withHit(LocalDateTime.now()))
            metrics.cacheHit(purpose)
            return Result(hit.response, hit.provider, hit.model, true)
        }

        var fresh = sidecar.generate(purpose, promptKey, variables)
        if (forbiddenTokenScanner.scan(fresh.text).matched) {
            // 1차 재생성 — 같은 프롬프트라도 LLM 출력은 매번 다르므로 대개 여기서 풀린다.
            fresh = sidecar.generate(purpose, promptKey, variables)
            if (forbiddenTokenScanner.scan(fresh.text).matched) {
                // 두 번 연속 걸리면 더 시도하지 않는다. 비용·지연도 문제지만,
                // 프롬프트 자체가 문제일 가능성이 높아 재시도로 풀릴 사안이 아니다.
                // 오염된 응답은 캐시에 넣지 않는다.
                return Result(forbiddenTokenFallback, "static", "safety-fallback", false)
            }
        }

        val entry = LlmCache.freshEntry(
            key, fresh.text, fresh.provider, fresh.model, purpose,
            fresh.promptTokens, fresh.completionTokens, LocalDateTime.now(),
        )
        cache.save(entry)
        metrics.cacheMiss(purpose, fresh.provider)
        return Result(fresh.text, fresh.provider, fresh.model, false)
    }

    data class Result(
        val text: String?,
        val provider: String?,
        val model: String?,
        val cached: Boolean,
    ) {
        /** Java 협력자(game `ResponseResolver`)가 `r.text()` 로 읽던 접근자를 그대로 유지. */
        fun text(): String? = text
    }
}
