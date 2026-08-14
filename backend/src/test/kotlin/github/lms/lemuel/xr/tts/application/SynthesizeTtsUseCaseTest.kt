package github.lms.lemuel.xr.tts.application

import github.lms.lemuel.xr.tts.application.port.out.TtsCachePort
import github.lms.lemuel.xr.tts.application.port.out.TtsJobQueuePort
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
 * SynthesizeTtsUseCase 단위 테스트 — 비동기(202 + 폴링) 계약 기준.
 *
 * 큐([TtsJobQueuePort])는 목킹하되, 기본 스텁은 **작업을 즉시 같은 스레드에서 실행**한다.
 * 그래야 "큐에 넘긴 뒤 실제로 무슨 일이 벌어지는가" 까지 한 테스트에서 검증할 수 있다.
 * 큐 포화 분기만 false 를 돌려주는 별도 스텁을 쓴다.
 */
class SynthesizeTtsUseCaseTest {

    private val cache: TtsCachePort = mock()
    private val sidecar: TtsSynthesisPort = mock()
    private val queue: TtsJobQueuePort = mock()
    private val uc = SynthesizeTtsUseCase(cache, sidecar, queue)

    /** 큐가 받아들이고 즉시 실행하도록. */
    private fun queueRunsInline() {
        whenever(queue.submit(any(), any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            (inv.arguments[1] as () -> Unit).invoke()
            true
        }
    }

    @Test
    fun `캐시 미스면 PENDING 자리표를 먼저 저장하고 큐에 넘긴다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize("안녕", "narrator-male-low", 1.0))
            .thenReturn(
                TtsSynthesisPort.SynthesisResult("https://r2/audio/abc.wav", 1500, "xtts-v2"),
            )
        queueRunsInline()

        val r = uc.submit("안녕", "narrator-male-low", 1.0)

        // 호출자에게는 jobId 만 준다 — 오디오는 아직 없다.
        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        val jobId = (r as SynthesizeTtsUseCase.Submission.Pending).jobId
        assertThat(jobId).hasSize(64) // sha256 hex

        val captor = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(captor.capture())

        // 1) 자리표가 *먼저* 저장돼야 동시 요청이 같은 job 으로 합쳐진다.
        val placeholder = captor.firstValue
        assertThat(placeholder.status).isEqualTo(TtsCache.PENDING)
        assertThat(placeholder.audioUrl).isNull()
        assertThat(placeholder.cacheKey).isEqualTo(jobId)

        // 2) 워커가 끝난 뒤 READY 로 승격되며 오디오가 채워진다.
        val done = captor.secondValue
        assertThat(done.status).isEqualTo(TtsCache.READY)
        assertThat(done.audioUrl).isEqualTo("https://r2/audio/abc.wav")
        assertThat(done.durationMs).isEqualTo(1500)
        assertThat(done.engine).isEqualTo("xtts-v2")
    }

    @Test
    fun `캐시 히트면 사이드카 호출없이 hitCount 증가 그리고 즉시 Ready`() {
        val existing = TtsCache(
            "key", "narrator-male-low", "xtts-v2",
            "https://r2/audio/cached.wav", 2000, 4,
            null, LocalDateTime.now().minusHours(1), null,
        )
        whenever(cache.findById(any())).thenReturn(Optional.of(existing))

        val r = uc.submit("안녕", "narrator-male-low", 1.0)

        assertThat(r).isEqualTo(
            SynthesizeTtsUseCase.Submission.Ready("https://r2/audio/cached.wav", 2000),
        )

        val captor = argumentCaptor<TtsCache>()
        verify(cache).save(captor.capture())
        assertThat(captor.firstValue.hitCount).isEqualTo(5)
        assertThat(captor.firstValue.lastHitAt).isNotNull()
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull())
        verify(queue, never()).submit(any(), any())
    }

    @Test
    fun `이미 PENDING 이면 중복 합성 없이 같은 jobId 를 돌려준다`() {
        val pending = TtsCache.pendingEntry("key", "v", LocalDateTime.now())
        whenever(cache.findById(any())).thenReturn(Optional.of(pending))

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        // 핵심: 큐에 또 넣지 않는다. 안 그러면 같은 문장이 워커를 n번 붙잡는다.
        verify(queue, never()).submit(any(), any())
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `FAILED 엔트리는 재시도 대상이다`() {
        val failed = TtsCache.pendingEntry("key", "v", LocalDateTime.now()).failed()
        whenever(cache.findById(any())).thenReturn(Optional.of(failed))
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))
        queueRunsInline()

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        verify(queue).submit(any(), any())
    }

    @Test
    fun `READY 인데 오디오가 비어 있으면 재시도한다`() {
        // 모순 상태 — 예전 동기 구현이 남긴 잔해나 부분 실패로 생길 수 있다.
        val hollow = TtsCache(
            "key", "v", "e", null, null, 1,
            null, LocalDateTime.now(), null, TtsCache.READY,
        )
        whenever(cache.findById(any())).thenReturn(Optional.of(hollow))
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))
        queueRunsInline()

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        verify(queue).submit(any(), any())
    }

    @Test
    fun `큐가 가득 차면 Rejected 그리고 유령 PENDING 을 남기지 않는다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(queue.submit(any(), any())).thenReturn(false)
        whenever(queue.queueDepth()).thenReturn(8)

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isEqualTo(SynthesizeTtsUseCase.Submission.Rejected(8))
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull())

        // 자리표를 PENDING 으로 방치하면 아무도 처리 안 하는데 폴링만 도는 유령이 된다.
        val captor = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(captor.capture())
        assertThat(captor.secondValue.status).isEqualTo(TtsCache.FAILED)
    }

    @Test
    fun `워커가 실패하면 FAILED 로 기록된다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException("사이드카 다운"))
        queueRunsInline()

        val r = uc.submit("안녕", "v", 1.0)

        // 제출 자체는 성공한다 — 실패는 폴링으로 드러난다.
        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        val captor = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(captor.capture())
        assertThat(captor.secondValue.status).isEqualTo(TtsCache.FAILED)
    }

    @Test
    fun `poll 은 상태별로 Ready Pending Failed 를 구분하고 없으면 null`() {
        whenever(cache.findById("nope")).thenReturn(Optional.empty())
        assertThat(uc.poll("nope")).isNull()

        whenever(cache.findById("p")).thenReturn(
            Optional.of(TtsCache.pendingEntry("p", "v", LocalDateTime.now())),
        )
        assertThat(uc.poll("p")).isEqualTo(SynthesizeTtsUseCase.JobView.Pending)

        whenever(cache.findById("f")).thenReturn(
            Optional.of(TtsCache.pendingEntry("f", "v", LocalDateTime.now()).failed()),
        )
        assertThat(uc.poll("f")).isEqualTo(SynthesizeTtsUseCase.JobView.Failed)

        whenever(cache.findById("r")).thenReturn(
            Optional.of(
                TtsCache.pendingEntry("r", "v", LocalDateTime.now())
                    .completed("xtts-v2", "https://r2/a.wav", 900),
            ),
        )
        assertThat(uc.poll("r"))
            .isEqualTo(SynthesizeTtsUseCase.JobView.Ready("https://r2/a.wav", 900))
    }

    @Test
    fun `동일 입력은 동일 캐시키 다른 입력은 다른키`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))
        queueRunsInline()

        uc.submit("텍스트A", "voice1", 1.0)
        uc.submit("텍스트A", "voice1", 1.0)
        uc.submit("텍스트B", "voice1", 1.0)

        val keys = argumentCaptor<String>()
        // 워커도 findById 를 부르므로 제출 경로의 첫 호출만 골라 본다.
        verify(cache, org.mockito.kotlin.atLeast(3)).findById(keys.capture())
        val submitKeys = listOf(keys.allValues[0], keys.allValues[2], keys.allValues[4])
        assertThat(submitKeys[0]).isEqualTo(submitKeys[1]) // 동일 입력 → 동일 키
        assertThat(submitKeys[0]).isNotEqualTo(submitKeys[2]) // 다른 텍스트 → 다른 키
    }

    @Test
    fun `null voiceId 와 null rate 도 안정적 키 생성`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(eq("안녕"), anyOrNull(), anyOrNull()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))
        queueRunsInline()

        val r = uc.submit("안녕", null, null)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
    }
}
