package github.lms.lemuel.xr.tts.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * tts_cache (V1 + V9 보강). cache_key = sha256(text+voice+rate).
 *
 * wav 는 [audioUrl] 에 base64 data URL 로 **행 안에 통째로** 들어간다. V1 의 컬럼 주석은
 * "실 wav 파일은 R2 또는 PVC" 라고 적고 storage_url 을 뒀지만 그 설계는 만들어지지 않았고,
 * 지금 어느 코드도 storage_url 을 읽거나 쓰지 않는다. V9 가 audio_url TEXT 를 더했고
 * 그쪽이 실제 저장소다. (V1 은 이미 적용된 마이그레이션이라 Flyway 체크섬 때문에 못 고친다.)
 *
 * 그래서 이 테이블이 이 시스템의 **유일한 영속 TTS 캐시**다. 사이드카의 /data/cache 는
 * emptyDir 이라 파드가 갈리면 사라진다 — 롤아웃을 건너 살아남는 것은 여기뿐이다.
 */
@Entity
@Table(name = "tts_cache")
class TtsCacheJpaEntity {

    @Id
    @Column(name = "cache_key", length = 255)
    var cacheKey: String? = null

    @Column(name = "voice_id", length = 50)
    var voiceId: String? = null

    @Column(name = "engine", length = 20)
    var engine: String? = null

    @Column(name = "audio_url", columnDefinition = "TEXT")
    var audioUrl: String? = null

    @Column(name = "duration_ms")
    var durationMs: Int? = null

    @Column(name = "hit_count", nullable = false)
    var hitCount: Int = 0

    @Column(name = "last_hit_at")
    var lastHitAt: LocalDateTime? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null

    /** READY | PENDING | FAILED — 비동기 전환으로 "오디오 없는 정상 행" 이 생겨 필요해졌다. */
    @Column(name = "status", length = 16, nullable = false)
    var status: String = "READY"
}
