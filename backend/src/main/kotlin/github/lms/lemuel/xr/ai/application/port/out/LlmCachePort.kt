package github.lms.lemuel.xr.ai.application.port.out

import github.lms.lemuel.xr.ai.domain.LlmCache
import java.util.Optional

/**
 * LLM 응답 캐시 out 포트 (ISP) — [github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase]
 * 가 실제로 쓰는 lookup / persist 두 연산만 노출.
 *
 * 포트는 도메인 타입([LlmCache]) 으로만 대화한다. JPA 엔티티는 어댑터 내부에만 존재.
 */
interface LlmCachePort {

    fun findByCacheKey(cacheKey: String): Optional<LlmCache>

    fun save(cache: LlmCache): LlmCache
}
