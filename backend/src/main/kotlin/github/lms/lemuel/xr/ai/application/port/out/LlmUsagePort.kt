package github.lms.lemuel.xr.ai.application.port.out

import github.lms.lemuel.xr.ai.domain.LlmUsage
import java.time.LocalDateTime

/**
 * LLM 사용량(토큰·비용) 기록 out 포트 (ISP) — 기록(save) 과 기간 조회(findByOccurredAtAfter)
 * 두 연산만 노출.
 *
 * 포트는 도메인 타입([LlmUsage]) 으로만 대화한다. JPA 엔티티는 어댑터 내부에만 존재.
 */
interface LlmUsagePort {

    fun save(usage: LlmUsage): LlmUsage

    fun findByOccurredAtAfter(after: LocalDateTime): List<LlmUsage>
}
