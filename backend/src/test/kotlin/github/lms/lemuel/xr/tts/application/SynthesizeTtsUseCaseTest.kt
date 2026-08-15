package github.lms.lemuel.xr.tts.application

import github.lms.lemuel.xr.tts.application.port.out.TtsCachePort
import github.lms.lemuel.xr.tts.application.port.out.TtsJobQueuePort
import github.lms.lemuel.xr.tts.application.port.out.TtsSynthesisPort
import github.lms.lemuel.xr.tts.domain.TtsCache
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        whenever(sidecar.synthesize("안녕", "narrator-male-low", 1.0, "ko"))
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
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull(), any())
        verify(queue, never()).submit(any(), any())
    }

    @Test
    fun `정말 진행 중인 PENDING 이면 중복 합성 없이 같은 jobId 를 돌려준다`() {
        val pending = TtsCache.pendingEntry("key", "v", LocalDateTime.now())
        whenever(cache.findById(any())).thenReturn(Optional.of(pending))
        whenever(queue.isInFlight(any())).thenReturn(true) // 워커가 실제로 들고 있다

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        // 핵심: 큐에 또 넣지 않는다. 안 그러면 같은 문장이 워커를 n번 붙잡는다.
        verify(queue, never()).submit(any(), any())
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull(), any())
    }

    /**
     * 이 버그의 실물이다. 배포·OOM·강제종료로 프로세스가 갈리면 큐(JVM 안)만 사라지고 행은
     * PENDING 으로 남는다. 예전 코드는 그 행만 보고 "진행 중" 이라며 큐에 안 넣었다 —
     * 다시 눌러도, 내일 눌러도 같은 jobId 만 돌아오고 소리는 영원히 안 났다.
     */
    @Test
    fun `아무도 안 들고 있는 PENDING 은 고아다 — 다시 큐에 올린다`() {
        val orphan = TtsCache.pendingEntry("key", "v", LocalDateTime.now())
        whenever(cache.findById(any())).thenReturn(Optional.of(orphan))
        whenever(queue.isInFlight(any())).thenReturn(false) // 프로세스가 갈렸다
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("https://r2/a.wav", 1500, "xtts-v2"))
        queueRunsInline()

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        verify(queue).submit(any(), any())
        verify(sidecar).synthesize(any(), anyOrNull(), anyOrNull(), any())

        // 그리고 실제로 살아난다 — 폴링이 결국 오디오를 받는다.
        val saved = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(saved.capture())
        assertThat(saved.lastValue.status).isEqualTo(TtsCache.READY)
        assertThat(saved.lastValue.audioUrl).isEqualTo("https://r2/a.wav")
    }

    @Test
    fun `고아를 되살리려다 큐가 가득 차면 FAILED 로 남긴다`() {
        // PENDING 인 채로 두면 고아를 고아로 갈아끼우는 셈이라 아무것도 나아지지 않는다.
        whenever(cache.findById(any()))
            .thenReturn(Optional.of(TtsCache.pendingEntry("key", "v", LocalDateTime.now())))
        whenever(queue.isInFlight(any())).thenReturn(false)
        whenever(queue.submit(any(), any())).thenReturn(false)
        whenever(queue.queueDepth()).thenReturn(8)

        val r = uc.submit("안녕", "v", 1.0)

        assertThat(r).isEqualTo(SynthesizeTtsUseCase.Submission.Rejected(8))
        val saved = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(saved.capture())
        assertThat(saved.lastValue.status).isEqualTo(TtsCache.FAILED)
    }

    @Test
    fun `FAILED 엔트리는 재시도 대상이다`() {
        val failed = TtsCache.pendingEntry("key", "v", LocalDateTime.now()).failed()
        whenever(cache.findById(any())).thenReturn(Optional.of(failed))
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
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
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
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
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull(), any())

        // 자리표를 PENDING 으로 방치하면 아무도 처리 안 하는데 폴링만 도는 유령이 된다.
        val captor = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(captor.capture())
        assertThat(captor.secondValue.status).isEqualTo(TtsCache.FAILED)
    }

    @Test
    fun `워커가 실패하면 FAILED 로 기록된다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
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
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
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
        whenever(sidecar.synthesize(eq("안녕"), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))
        queueRunsInline()

        val r = uc.submit("안녕", null, null)

        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
    }

    // ── 언어 (ko · en) ──────────────────────────────────────────────────────

    @Test
    fun `language 를 생략하면 사이드카에 ko 로 간다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e", "ko"))
        queueRunsInline()

        uc.submit("안녕", "v", 1.0)

        val lang = argumentCaptor<String>()
        verify(sidecar).synthesize(any(), anyOrNull(), anyOrNull(), lang.capture())
        assertThat(lang.firstValue).isEqualTo("ko")
    }

    @Test
    fun `en 요청은 사이드카까지 en 으로 전달된다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e", "en"))
        queueRunsInline()

        uc.submit("David and Goliath", "v", 1.0, "en")

        val lang = argumentCaptor<String>()
        verify(sidecar).synthesize(any(), anyOrNull(), anyOrNull(), lang.capture())
        assertThat(lang.firstValue).isEqualTo("en")
    }

    @Test
    fun `대소문자와 공백은 정규화한다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e", "en"))
        queueRunsInline()

        uc.submit("hello", "v", 1.0, " EN ")

        val lang = argumentCaptor<String>()
        verify(sidecar).synthesize(any(), anyOrNull(), anyOrNull(), lang.capture())
        assertThat(lang.firstValue).isEqualTo("en")
    }

    @Test
    fun `지원하지 않는 언어는 큐에 들어가기 전에 거절한다`() {
        // 사이드카까지 갔다면 400 이 워커 안에서 터져 사용자에겐 이유 없는 failed 로만 보인다.
        assertThatThrownBy { uc.submit("hola", "v", 1.0, "es") }
            .isInstanceOf(IllegalArgumentException::class.java)

        verify(queue, never()).submit(any(), any())
        verify(sidecar, never()).synthesize(any(), anyOrNull(), anyOrNull(), any())
        verify(cache, never()).save(any())
    }

    @Test
    fun `같은 문장이라도 언어가 다르면 캐시키가 갈린다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("u", 1, "e"))
        queueRunsInline()

        val ko = uc.submit("Amen", "v", 1.0, "ko") as SynthesizeTtsUseCase.Submission.Pending
        val en = uc.submit("Amen", "v", 1.0, "en") as SynthesizeTtsUseCase.Submission.Pending

        // 갈리지 않으면 먼저 구워진 쪽이 이기고 나머지는 엉뚱한 언어를 캐시 히트로 받는다.
        assertThat(ko.jobId).isNotEqualTo(en.jobId)
    }

    /**
     * 2026-08-15 이전에는 이 자리에 `ko 키는 언어 접두가 없던 시절과 같다` 가 있었다. 그 테스트는
     * 옛 ko 캐시가 계속 히트되는 것을 *지키는* 테스트였다. 이제는 정반대를 지켜야 한다 —
     * 한국어 발음 전처리(g2p)가 붙어서, 그 전에 구워진 ko 오디오는 "외국인이 철자대로 읽는"
     * 소리이므로 재사용하면 안 된다. 반대 성질로 갈아 끼운다.
     */
    @Test
    fun `발음 전처리 전에 구워진 ko 행은 캐시 히트가 되지 않는다`() {
        val legacyKey = sha256("안녕|narrator-male-low|1.0")
        // 옛 세대의 READY 행이 DB 에 그대로 남아 있는 상황(실제로 50행·82MB 있었다).
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(cache.findById(legacyKey)).thenReturn(
            Optional.of(
                TtsCache.pendingEntry(legacyKey, "narrator-male-low", LocalDateTime.now())
                    .completed("xtts-v2", "data:audio/wav;base64,AAAA", 1500),
            ),
        )
        whenever(queue.submit(any(), any())).thenReturn(true)

        val r = uc.submit("안녕", "narrator-male-low", 1.0, "ko")

        // Ready 로 돌아오면 옛 소리가 그대로 나간다 = 배포해도 사용자에겐 아무것도 안 바뀐다.
        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Pending::class.java)
        assertThat((r as SynthesizeTtsUseCase.Submission.Pending).jobId).isNotEqualTo(legacyKey)
    }

    /**
     * 이 테스트는 원래 `en 캐시는 한국어 발음 전처리에 휘말려 무효화되지 않는다` 였다.
     * 그때는 세대 표식이 ko 에만 있었고(`{ko: g2p2}`), en 키에는 접두가 아예 없었다.
     *
     * 2026-08-15 엔진을 XTTS-v2 에서 Gemini 로 바꾸면서 `en` 에도 세대를 붙였다 — 발음
     * 전처리가 아니라 **엔진**이 바뀌었으니 XTTS 가 구운 영어 오디오도 전부 옛 소리다.
     * 그래서 옛 단언(en 은 Ready)은 더 이상 맞지 않는다. 다만 그때 지키려던 성질 자체는
     * 그대로 살아 있어야 한다 — **세대는 언어별로 독립이다.** 한 언어를 올렸다고 다른
     * 언어까지 휩쓸리면 안 구워도 될 오디오를 다시 굽는다. 그걸 여기서 계속 지킨다.
     */
    @Test
    fun `세대 표식은 언어별로 독립이다 — en 조회는 en 세대 키를 쓴다`() {
        // 세대를 올릴 때 여기도 같이 올려야 한다. 손이 가는 게 의도다 — 이 리터럴이
        // 빨개지는 것이 "캐시를 통째로 버리는 변경을 하고 있다" 는 마지막 확인이다.
        // (gem1 -> gem2: 2026-08-15 WAV -> MP3)
        val enKey = sha256("gem2|en|David|v|1.0")
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(cache.findById(enKey)).thenReturn(
            Optional.of(
                TtsCache.pendingEntry(enKey, "v", LocalDateTime.now())
                    .completed("gemini-3.1-flash-tts-preview", "data:audio/wav;base64,AAAA", 1500),
            ),
        )

        val r = uc.submit("David", "v", 1.0, "en")

        // en 세대 키로 구워 둔 행이 히트해야 한다. 여기서 Pending 이 나오면 en 조회가
        // 제 언어의 세대가 아닌 다른 값(ko 세대·빈 접두)을 쓰고 있다는 뜻이다.
        assertThat(r).isInstanceOf(SynthesizeTtsUseCase.Submission.Ready::class.java)

        // 같은 문장·같은 화자라도 언어가 다르면 키가 달라야 한다. 이게 깨지면 en 요청이
        // ko 오디오를 캐시 히트로 받아간다 — 소리로만 드러나는 고장이다.
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(queue.submit(any(), any())).thenReturn(true)
        val ko = uc.submit("David", "v", 1.0, "ko")
        assertThat((ko as SynthesizeTtsUseCase.Submission.Pending).jobId).isNotEqualTo(enKey)
    }

    private fun sha256(s: String): String = java.util.HexFormat.of().formatHex(
        java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)),
    )

    @Test
    fun `사이드카가 다른 언어를 돌려주면 저장하지 않고 실패로 떨어뜨린다`() {
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        // en 을 요청했는데 ko 오디오가 왔다 — 저장하면 잘못된 오디오가 캐시에 눌러앉는다.
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("https://r2/ko.wav", 1500, "xtts-v2", "ko"))
        queueRunsInline()

        uc.submit("David", "v", 1.0, "en")

        val saved = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(saved.capture())
        val last = saved.lastValue
        assertThat(last.status).isEqualTo(TtsCache.FAILED)
        assertThat(last.audioUrl).isNull()
    }

    @Test
    fun `언어를 안 돌려주는 옛 사이드카와도 동작한다`() {
        // 새 백엔드 + 옛 사이드카가 공존하는 롤아웃 구간. language 키가 아예 없다.
        whenever(cache.findById(any())).thenReturn(Optional.empty())
        whenever(sidecar.synthesize(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(TtsSynthesisPort.SynthesisResult("https://r2/a.wav", 1500, "xtts-v2", null))
        queueRunsInline()

        uc.submit("안녕", "v", 1.0, "ko")

        val saved = argumentCaptor<TtsCache>()
        verify(cache, times(2)).save(saved.capture())
        assertThat(saved.lastValue.status).isEqualTo(TtsCache.READY)
    }
}
