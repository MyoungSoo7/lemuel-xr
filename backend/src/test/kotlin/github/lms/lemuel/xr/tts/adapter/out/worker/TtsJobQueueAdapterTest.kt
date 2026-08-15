package github.lms.lemuel.xr.tts.adapter.out.worker

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 큐 어댑터 단위 테스트 — 관심사는 오직 **소유 여부([TtsJobQueueAdapter.isInFlight])** 다.
 *
 * 유스케이스가 "이 PENDING 행이 고아인가" 를 이 한 가지 신호로 판정한다. 여기가 틀리면
 * 둘 중 하나가 벌어진다: true 를 너무 오래 들고 있으면 되살아나야 할 작업이 영영 막히고,
 * 너무 일찍 놓으면 진행 중인 작업을 고아로 오인해 같은 문장을 중복 합성한다.
 */
class TtsJobQueueAdapterTest {

    private val adapter = TtsJobQueueAdapter(queueCapacity = 4, drainSeconds = 5)

    /** 지정한 시간 안에 조건이 참이 될 때까지 기다린다 — 워커가 다른 스레드라 즉시 단정할 수 없다. */
    private fun eventually(block: () -> Unit) =
        await().atMost(Duration.ofSeconds(5)).untilAsserted(block)

    @Test
    fun `모르는 jobId 는 들고 있지 않다`() {
        assertThat(adapter.isInFlight("한 번도 본 적 없는 키")).isFalse()
    }

    @Test
    fun `합성 중인 작업도 들고 있다고 답한다`() {
        // 큐에서 꺼내 *실행 중인* 작업은 이미 큐에서 빠져 있다. 큐 크기만 봤다면 여기서
        // 놓쳤을 것이고, 그게 곧 중복 합성이다.
        val 작업시작 = CountDownLatch(1)
        val 놓아주기 = CountDownLatch(1)

        adapter.submit("job-A") {
            작업시작.countDown()
            놓아주기.await(5, TimeUnit.SECONDS)
        }

        assertThat(작업시작.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(adapter.queueDepth()).isZero() // 큐에는 없다
        assertThat(adapter.isInFlight("job-A")).isTrue() // 그래도 들고 있다

        놓아주기.countDown()
        eventually { assertThat(adapter.isInFlight("job-A")).isFalse() }
    }

    @Test
    fun `대기 중인 작업도 들고 있다고 답한다`() {
        val 놓아주기 = CountDownLatch(1)
        adapter.submit("먼저") { 놓아주기.await(5, TimeUnit.SECONDS) }
        adapter.submit("뒤에") {}

        assertThat(adapter.isInFlight("뒤에")).isTrue()

        놓아주기.countDown()
        eventually { assertThat(adapter.isInFlight("뒤에")).isFalse() }
    }

    @Test
    fun `끝나면 놓아준다 — 안 놓으면 재시도가 영원히 막힌다`() {
        adapter.submit("job-B") {}
        eventually { assertThat(adapter.isInFlight("job-B")).isFalse() }
    }

    @Test
    fun `작업이 터져도 놓아준다`() {
        // finally 가 없으면 실패한 문장이 FAILED 로 기록되고도 "진행 중" 으로 보여
        // 재요청이 큐에 못 들어간다 — 고치려던 버그가 형태만 바꿔 되돌아온다.
        adapter.submit("job-C") { throw RuntimeException("사이드카 다운") }
        eventually { assertThat(adapter.isInFlight("job-C")).isFalse() }
    }

    @Test
    fun `큐가 가득 차 거절하면 들고 있지 않다`() {
        val 놓아주기 = CountDownLatch(1)
        adapter.submit("실행중") { 놓아주기.await(5, TimeUnit.SECONDS) }
        repeat(4) { i -> assertThat(adapter.submit("대기$i") {}).isTrue() }

        assertThat(adapter.submit("거절될것") {}).isFalse()
        // 거절된 작업을 들고 있다고 답하면, 유스케이스가 FAILED 로 남긴 행을
        // 재요청 때 "진행 중" 으로 오인해 되살릴 수 없게 된다.
        assertThat(adapter.isInFlight("거절될것")).isFalse()

        놓아주기.countDown()
    }

    /**
     * 배포가 진행 중인 합성을 죽이지 않는지 본다.
     *
     * 이 테스트가 없던 동안 [TtsJobQueueAdapter.shutdown] 은 곧장 `shutdownNow()` 였고,
     * 롤아웃 때마다 그때 굽던 문장이 버려졌다 (2026-08-15 프리웜 57/93 에서 실측).
     * 사용자에게는 소리 버튼이 이유 없이 사라지는 것으로만 보이는 종류의 고장이라,
     * 로그도 알림도 이걸 대신 잡아주지 못한다.
     */
    @Test
    fun `종료할 때 진행 중인 합성을 끝까지 기다린다`() {
        val 어댑터 = TtsJobQueueAdapter(queueCapacity = 4, drainSeconds = 5)
        val 작업시작 = CountDownLatch(1)
        val 끝까지갔다 = java.util.concurrent.atomic.AtomicBoolean(false)

        어댑터.submit("굽는중") {
            작업시작.countDown()
            Thread.sleep(300) // 합성이 진행 중인 상태를 흉내 낸다
            끝까지갔다.set(true)
        }
        assertThat(작업시작.await(5, TimeUnit.SECONDS)).isTrue()

        어댑터.shutdown()

        // shutdownNow() 였다면 sleep 이 인터럽트돼 여기서 false 였다.
        assertThat(끝까지갔다.get()).isTrue()
    }

    /**
     * 기다림에는 상한이 있어야 한다. 없으면 파드가 유예시간을 넘겨 SIGKILL 로 죽고,
     * 그때는 배수를 시도했다는 사실만 남고 실제로는 아무것도 못 끝낸 것이 된다.
     */
    @Test
    fun `상한을 넘기면 기다림을 포기한다`() {
        val 어댑터 = TtsJobQueueAdapter(queueCapacity = 4, drainSeconds = 1)
        val 놓아주기 = CountDownLatch(1)
        val 작업시작 = CountDownLatch(1)

        어댑터.submit("영원히안끝남") {
            작업시작.countDown()
            놓아주기.await(30, TimeUnit.SECONDS)
        }
        assertThat(작업시작.await(5, TimeUnit.SECONDS)).isTrue()

        val 시작 = System.nanoTime()
        어댑터.shutdown()
        val 걸린초 = (System.nanoTime() - 시작) / 1_000_000_000.0

        // 상한(1초) 근처에서 돌아와야 한다. 30초를 다 기다렸다면 상한이 안 먹은 것이다.
        assertThat(걸린초).isLessThan(10.0)
        놓아주기.countDown()
    }
}
