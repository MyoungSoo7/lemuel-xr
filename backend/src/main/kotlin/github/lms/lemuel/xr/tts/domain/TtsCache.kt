package github.lms.lemuel.xr.tts.domain

import java.time.LocalDateTime

/**
 * TtsCache 애그리거트 — 순수 도메인 모델 (Hibernate 무관).
 *
 * cache_key = sha256(text+voice+rate). [TtsCachePort] 는 이 타입만 노출하고,
 * Hibernate 엔티티는 `TtsCachePersistenceAdapter` 안에만 갇힌다.
 *
 * 불변 data class — 상태 변경은 새 인스턴스를 만드는 `copy`/팩토리로만 표현한다.
 */
data class TtsCache(
    val cacheKey: String,
    val voiceId: String?,
    val engine: String?,
    val audioUrl: String?,
    val durationMs: Int?,
    val hitCount: Int?,
    val lastHitAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
) {

    /** 캐시 히트 기록: hitCount +1, lastHitAt 갱신한 새 인스턴스 반환. */
    fun registerHit(hitAt: LocalDateTime): TtsCache =
        copy(hitCount = (hitCount ?: 0) + 1, lastHitAt = hitAt)

    companion object {
        /**
         * 사이드카에서 갓 합성된 오디오를 담는 신규 캐시 엔트리 — 히트 0, 만료 없음.
         * miss 경로의 raw 생성자(트레일링 `0, null, now, null`)를 대체하는 명명 팩토리.
         */
        fun freshEntry(
            cacheKey: String,
            voiceId: String?,
            engine: String?,
            audioUrl: String?,
            durationMs: Int?,
            createdAt: LocalDateTime,
        ): TtsCache =
            TtsCache(
                cacheKey = cacheKey,
                voiceId = voiceId,
                engine = engine,
                audioUrl = audioUrl,
                durationMs = durationMs,
                hitCount = 0,
                lastHitAt = null,
                createdAt = createdAt,
                expiresAt = null,
            )
    }
}
