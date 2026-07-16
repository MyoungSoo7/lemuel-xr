package github.lms.lemuel.xr.tts.adapter.out.persistence

import github.lms.lemuel.xr.tts.application.port.out.TtsCachePort
import github.lms.lemuel.xr.tts.domain.TtsCache
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * [TtsCachePort] 를 Spring Data JPA 로 구현. 포트(도메인) ↔ JpaRepository(엔티티) 사이 어댑터.
 *
 * 이 클래스가 [TtsCacheJpaEntity] 를 import 하는 **유일한** 곳이다 — Hibernate 누수 차단.
 */
@Component
class TtsCachePersistenceAdapter(
    private val jpa: TtsCacheJpaRepository,
) : TtsCachePort {

    override fun findById(cacheKey: String): Optional<TtsCache> =
        jpa.findById(cacheKey).map(::toDomain)

    override fun save(entry: TtsCache): TtsCache =
        toDomain(jpa.save(toEntity(entry)))

    private fun toDomain(e: TtsCacheJpaEntity): TtsCache =
        TtsCache(
            cacheKey = e.cacheKey!!,
            voiceId = e.voiceId,
            engine = e.engine,
            audioUrl = e.audioUrl,
            durationMs = e.durationMs,
            hitCount = e.hitCount,
            lastHitAt = e.lastHitAt,
            createdAt = e.createdAt,
            expiresAt = e.expiresAt,
        )

    private fun toEntity(d: TtsCache): TtsCacheJpaEntity =
        TtsCacheJpaEntity().apply {
            cacheKey = d.cacheKey
            voiceId = d.voiceId
            engine = d.engine
            audioUrl = d.audioUrl
            durationMs = d.durationMs
            hitCount = d.hitCount ?: 0
            lastHitAt = d.lastHitAt
            createdAt = d.createdAt
            expiresAt = d.expiresAt
        }
}
