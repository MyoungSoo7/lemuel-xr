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
        // 진행 중인 합성은 몇 분이 걸릴 수 있다. 종료를 무한정 기다리지 않는다 — 끝난 결과는
        // 캐시 행에 남아 있고, 못 끝낸 건 PENDING 행으로 남는다. 그 행은 새 프로세스의
        // [inFlight] 에 없으므로 재요청 때 고아로 판정돼 다시 큐에 오른다 ([isInFlight] 참조).
        //
        // 이 문장은 예전엔 사실이 아니었다. 유스케이스가 PENDING 행만 보고 "진행 중" 이라며
        // 큐에 안 넣어, 배포에 걸린 문장은 영구히 소리가 나지 않았다. 소유 여부를 함께 보게
        // 바꾼 지금에야 맞는 말이 됐다.
        executor.shutdownNow()
    }
}
