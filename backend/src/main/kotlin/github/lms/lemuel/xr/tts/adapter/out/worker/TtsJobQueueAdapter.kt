package github.lms.lemuel.xr.tts.adapter.out.worker

import github.lms.lemuel.xr.tts.application.port.out.TtsJobQueuePort
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * [TtsJobQueuePort] 를 단일 스레드 + 유계 큐로 구현.
 *
 * **워커가 1개인 것은 제약이 아니라 의도다.** 사이드카 파드가 하나뿐이고 CPU 합성이라,
 * 동시에 두 건을 밀어넣으면 서로 CPU 를 뺏어 둘 다 느려진다(합계 처리량은 그대로거나
 * 더 나빠진다). 직렬화해서 앞의 것부터 확실히 끝내는 편이 낫다.
 */
@Component
class TtsJobQueueAdapter(
    @Value("\${tts.queue-capacity:8}") queueCapacity: Int,
    /**
     * 종료할 때 **진행 중인 합성 한 건**을 기다려 주는 시간.
     *
     * 60초인 근거: 프로덕션 실측에서 한 건이 12~49초 걸린다(280자에 49초). 여유를 두되,
     * 파드 유예시간(`terminationGracePeriodSeconds: 90`) 안에서 끝나야 한다 — 유예시간을
     * 넘기면 kubelet 이 SIGKILL 을 보내고 이 대기는 아무 의미가 없어진다.
     */
    @Value("\${tts.shutdown-drain-seconds:60}") private val drainSeconds: Long,
) : TtsJobQueuePort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val queue = LinkedBlockingQueue<Runnable>(queueCapacity)

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, queue,
    ) { r -> Thread(r, "tts-worker").apply { isDaemon = true } }

    /**
     * 지금 이 프로세스가 들고 있는 jobId — 큐 대기 중 + 워커가 합성 중.
     *
     * 큐를 직접 뒤지지 않고 따로 두는 이유는 두 가지다. 큐에 담기는 것은 `Runnable` 이라
     * 어떤 jobId 인지 알 수 없고, 워커가 꺼내 *실행 중인* 작업은 이미 큐에서 빠져 있어
     * `queue` 만 봐서는 안 보인다. 합성 중인 것을 놓치면 그게 곧 중복 합성이다.
     */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun submit(jobId: String, task: () -> Unit): Boolean =
        try {
            // execute 보다 *먼저* 표시한다. 워커가 즉시 집어가 끝내 버리는 경우까지 포함해
            // "큐에 있다" 상태가 한 순간도 비지 않게 한다.
            inFlight.add(jobId)
            executor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    // 워커 스레드에서 예외가 새어나가면 스레드만 죽고 아무도 모른다.
                    // 작업 자체의 실패 처리(FAILED 기록)는 task 안에서 하고, 여기는 최후 방어선이다.
                    log.error("TTS job {} 최후 방어선에서 잡힘", jobId, e)
                } finally {
                    // 끝났으면 놓아준다. 안 놓으면 재시도(FAILED 후 재요청)가 영원히 막힌다.
                    inFlight.remove(jobId)
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            inFlight.remove(jobId)
            log.warn("TTS 큐 포화 — job {} 거절 (depth={})", jobId, queue.size)
            false
        }

    override fun queueDepth(): Int = queue.size

    override fun isInFlight(jobId: String): Boolean = inFlight.contains(jobId)

    @PreDestroy
    fun shutdown() {
        // **진행 중인 합성을 끝까지 기다렸다 죽는다.**
        //
        // 예전에는 여기서 곧장 `shutdownNow()` 를 불렀다. 그러면 워커 스레드가 인터럽트돼
        // 굽던 문장이 버려지고, 사용자 눈에는 소리 버튼이 아무 설명 없이 사라진다
        // (`onUnavailable="hide"`). 2026-08-15 프리웜 57/93 에서 실제로 그렇게 한 건을 잃었다 —
        // 사이드카는 오디오를 다 만들어 200 으로 돌려줬는데 백엔드가 그 순간 교체됐다.
        //
        // 배포는 앞으로도 일어난다. 이 리포는 `replicas: 1` 이라 롤아웃 = 파드 교체이고,
        // 교체가 곧 "그때 듣고 있던 사람의 소리가 사라짐" 이 되지 않게 하려면 여기서
        // 버티는 수밖에 없다.
        //
        // 순서: 새 작업 접수를 닫고(shutdown) → 진행 중 한 건을 기다리고 → 그래도 안 끝나면
        // 포기(shutdownNow). 큐에 대기만 하던 것들은 어차피 이 대기 안에 다 못 끝내므로
        // 버려지지만, 그 행들은 PENDING 으로 남아 재요청 때 고아로 판정돼 다시 큐에 오른다
        // ([isInFlight] 참조).
        executor.shutdown()
        try {
            if (!executor.awaitTermination(drainSeconds, TimeUnit.SECONDS)) {
                log.warn("TTS 워커가 {}초 안에 안 끝났다 — 남은 작업을 버리고 종료한다", drainSeconds)
                executor.shutdownNow()
            } else {
                log.info("TTS 워커 정상 배수 완료 — 진행 중이던 합성을 끝내고 종료한다")
            }
        } catch (_: InterruptedException) {
            // 우리를 기다리던 쪽이 먼저 포기했다. 더 붙잡을 명분이 없다.
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
