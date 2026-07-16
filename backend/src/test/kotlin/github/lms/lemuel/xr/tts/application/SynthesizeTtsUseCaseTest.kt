package github.lms.lemuel.xr.tts.application

import github.lms.lemuel.xr.tts.application.port.out.TtsCachePort
import github.lms.lemuel.xr.tts.application.port.out.TtsSynthesisPort
import github.lms.lemuel.xr.tts.domain.TtsCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

/**
 * SynthesizeTtsUseCase 단위 테스트 — 캐시 히트 / 미스(사이드카 호출) / 키 안정성 분기 커버.
 *
 * 외부 TTS 엔진([TtsSynthesisPort]) 은 mockito-kotlin 으로 목킹 — 실제 오디오 생성 없음.
 * 포트는 순수 도메인 [TtsCache] 로만 소통한다 — Hibernate 엔티티는 등장하지 않는다.
 */
class SynthesizeTtsUseCaseTest {

    private val cache: TtsCachePort = mock()
    private val sidecar: TtsSynthesisPort = mock()
    private val uc = SynthesizeTtsUseCase(cache, sidecar)

    @Test
    fun `캐시 미스면 사이드카 호출하고 저장 그리고 cached false`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize("안녕", "narrator-male-low", 1.0))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult(
                    "https://r2/audio/abc.wav", 1500, "xtts-v2",
                ),
            )

        val r = uc.execute("안녕", "narrator-male-low", 1.0)

        assertThat(r.cached).isFalse()
        assertThat(r.audioUrl).isEqualTo("https://r2/audio/abc.wav")
        assertThat(r.durationMs).isEqualTo(1500)

        val captor = argumentCaptor<TtsCache>()
        verify(cache).save(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.cacheKey).hasSize(64) // sha256 hex
        assertThat(saved.voiceId).isEqualTo("narrator-male-low")
        assertThat(saved.engine).isEqualTo("xtts-v2")
        assertThat(saved.audioUrl).isEqualTo("https://r2/audio/abc.wav")
        assertThat(saved.hitCount).isZero()
        assertThat(saved.createdAt).isNotNull()
    }

    @Test
    fun `캐시 히트면 사이드카 호출없이 hitCount 증가 그리고 cached true`() {
        val existing = TtsCache(
            "key", "narrator-male-low", "xtts-v2",
            "https://r2/audio/cached.wav", 2000, 4,
            null, LocalDateTime.now().minusHours(1), null,
        )
        whenever(cache.findById(any())).thenReturn(Optional.of(existing))

        val r = uc.execute("안녕", "narrator-male-low", 1.0)

        assertThat(r.cached).isTrue()
        assertThat(r.audioUrl).isEqualTo("https://r2/audio/cached.wav")
        assertThat(r.durationMs).isEqualTo(2000)

        // 도메인 data class 는 불변 — 증가된 hitCount/lastHitAt 는 save 로 영속화된다.
        val captor = argumentCaptor<TtsCache>()
        verify(cache).save(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.hitCount).isEqualTo(5)
        assertThat(saved.lastHitAt).isNotNull()
        assertThat(saved.cacheKey).isEqualTo("key")
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `동일 입력은 동일 캐시키 다른 입력은 다른키`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))

        uc.execute("텍스트A", "voice1", 1.0)
        uc.execute("텍스트A", "voice1", 1.0)
        uc.execute("텍스트B", "voice1", 1.0)

        val keys = argumentCaptor<String>()
        verify(cache, times(3)).findById(keys.capture())
        val captured = keys.allValues
        assertThat(captured[0]).isEqualTo(captured[1]) // 동일 입력 → 동일 키
        assertThat(captured[0]).isNotEqualTo(captured[2]) // 다른 텍스트 → 다른 키
    }

    @Test
    fun `null voiceId 와 null rate 도 안정적 키 생성`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(eq("안녕"), anyOrNull(), anyOrNull()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))

        val r = uc.execute("안녕", null, null)

        assertThat(r.cached).isFalse()
        verify(cache).save(any())
    }
}
