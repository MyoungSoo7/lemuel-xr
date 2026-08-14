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
    val status: String = READY,
) {

    /** 캐시 히트 기록: hitCount +1, lastHitAt 갱신한 새 인스턴스 반환. */
    fun registerHit(hitAt: LocalDateTime): TtsCache =
        copy(hitCount = (hitCount ?: 0) + 1, lastHitAt = hitAt)

    /** 재생 가능한 상태인가 — 오디오까지 실제로 들어있는지 함께 본다. */
    fun isPlayable(): Boolean = status == READY && !audioUrl.isNullOrBlank()

    /** 워커가 합성을 마쳤을 때: PENDING → READY 로 승격하고 오디오를 채운다. */
    fun completed(engine: String?, audioUrl: String?, durationMs: Int?): TtsCache =
        copy(engine = engine, audioUrl = audioUrl, durationMs = durationMs, status = READY)

    /** 워커가 실패했을 때. 오디오는 비운 채 FAILED 로 남겨 재시도 대상이 되게 한다. */
    fun failed(): TtsCache = copy(audioUrl = null, durationMs = null, status = FAILED)

    companion object {
        /** 합성 완료. 오디오가 들어있다. */
        const val READY: String = "READY"

        /** 큐에 올라갔거나 워커가 합성 중. 오디오가 아직 없다 — 이것도 정상 상태다. */
        const val PENDING: String = "PENDING"

        /** 합성 실패. 같은 텍스트가 다시 요청되면 재시도한다. */
        const val FAILED: String = "FAILED"

        /**
         * 큐 등록 시점의 자리표(placeholder) 엔트리 — 오디오 없이 PENDING 으로만 존재한다.
         *
         * 이 행이 *먼저 커밋돼야* 같은 텍스트에 대한 동시 요청이 중복 합성을 일으키지 않고
         * 같은 jobId 를 돌려받는다. cacheKey 가 곧 jobId 다.
         */
        fun pendingEntry(
            cacheKey: String,
            voiceId: String?,
            createdAt: LocalDateTime,
        ): TtsCache =
            TtsCache(
                cacheKey = cacheKey,
                voiceId = voiceId,
                engine = null,
                audioUrl = null,
                durationMs = null,
                hitCount = 0,
                lastHitAt = null,
                createdAt = createdAt,
                expiresAt = null,
                status = PENDING,
            )

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
