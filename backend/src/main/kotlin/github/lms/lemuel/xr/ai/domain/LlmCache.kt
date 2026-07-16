package github.lms.lemuel.xr.ai.domain

import java.time.LocalDateTime

/**
 * LLM 응답 캐시 1건 — 불변 도메인 모델.
 *
 * Hibernate `LlmCacheJpaEntity` 로부터 격리된 순수 도메인 타입.
 * 영속성 어댑터(`LlmCachePersistenceAdapter`) 에서만 엔티티 ↔ 이 타입으로 매핑한다.
 * 애플리케이션(포트·유즈케이스) 은 절대 JPA 엔티티를 알지 못한다.
 *
 * @property cacheKey         캐시 키 (PK, promptKey + variables 의 SHA-256)
 * @property response         캐시된 응답 본문
 * @property provider         LLM 제공자 (nullable)
 * @property model            모델명 (nullable)
 * @property purpose          용도 (meditation / scene / ...) (nullable)
 * @property promptTokens     프롬프트 토큰 수 (nullable)
 * @property completionTokens 완성 토큰 수 (nullable)
 * @property hitCount         캐시 히트 누적 횟수
 * @property lastHitAt        마지막 히트 시각 (nullable)
 * @property createdAt        생성 시각
 * @property expiresAt        만료 시각 (nullable)
 */
data class LlmCache(
    val cacheKey: String,
    val response: String?,
    val provider: String?,
    val model: String?,
    val purpose: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val hitCount: Int,
    val lastHitAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
) {

    /**
     * 히트 1회를 기록한 새 인스턴스를 반환한다 (불변).
     * `hitCount + 1`, `lastHitAt = hitAt` 로 갱신된 복사본.
     */
    fun withHit(hitAt: LocalDateTime): LlmCache =
        copy(hitCount = hitCount + 1, lastHitAt = hitAt)

    companion object {
        /**
         * 사이드카에서 갓 생성된 응답을 담는 신규 캐시 엔트리 — 히트 0, 만료 없음.
         * miss 경로의 raw 생성자(트레일링 `0, null, now, null`)를 대체하는 명명 팩토리.
         */
        fun freshEntry(
            cacheKey: String,
            response: String?,
            provider: String?,
            model: String?,
            purpose: String?,
            promptTokens: Int?,
            completionTokens: Int?,
            createdAt: LocalDateTime,
        ): LlmCache =
            LlmCache(
                cacheKey = cacheKey,
                response = response,
                provider = provider,
                model = model,
                purpose = purpose,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                hitCount = 0,
                lastHitAt = null,
                createdAt = createdAt,
                expiresAt = null,
            )
    }
}
