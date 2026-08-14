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

    private val adapter = TtsJobQueueAdapter(queueCapacity = 4)

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
}
