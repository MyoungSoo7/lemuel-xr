package github.lms.lemuel.xr.ai.application.port.out

/**
 * LLM 메트릭 아웃바운드 포트 (DIP).
 *
 * 애플리케이션 계층([github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase])
 * 이 Micrometer 같은 구체 메트릭 라이브러리에 의존하지 않도록 격리한다.
 * `values` 컨텍스트의 `PracticeMetricsPort` 패턴과 동일.
 *
 * 메트릭: `llm.cache.hit{purpose}` / `llm.cache.miss{purpose,provider}` /
 * `llm.generation.disabled{purpose}`. Grafana 의 *AI 비용·캐시* row 에서 hit rate 차트.
 */
interface LlmMetricsPort {

    /** 생성 비활성화 상태에서 요청이 들어옴 — `llm.generation.disabled{purpose}`. */
    fun generationDisabled(purpose: String)

    /** 캐시 히트 — `llm.cache.hit{purpose}`. */
    fun cacheHit(purpose: String)

    /** 캐시 미스 (사이드카 호출 발생) — `llm.cache.miss{purpose,provider}`. */
    fun cacheMiss(purpose: String, provider: String?)
}
