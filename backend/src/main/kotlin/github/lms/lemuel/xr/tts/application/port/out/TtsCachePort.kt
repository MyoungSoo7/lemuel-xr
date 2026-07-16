package github.lms.lemuel.xr.tts.application.port.out

import github.lms.lemuel.xr.tts.domain.TtsCache
import java.util.Optional

/**
 * tts_cache 영속화 아웃바운드 포트.
 *
 * ISP — 애플리케이션(SynthesizeTtsUseCase)이 실제로 쓰는 2개 연산만 노출한다.
 * `JpaRepository` 의 나머지 CRUD 전면은 의도적으로 감춘다.
 *
 * 포트는 순수 도메인 타입 [TtsCache] 로만 소통한다 — Hibernate 엔티티는
 * 어댑터(persistence) 안에만 갇힌다.
 */
interface TtsCachePort {

    /** cache_key(sha256) 로 캐시 항목 조회. */
    fun findById(cacheKey: String): Optional<TtsCache>

    /** 캐시 항목 저장. */
    fun save(entry: TtsCache): TtsCache
}
