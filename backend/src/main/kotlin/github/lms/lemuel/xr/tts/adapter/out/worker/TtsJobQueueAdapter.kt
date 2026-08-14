package github.lms.lemuel.xr.tts.adapter.out.worker

import github.lms.lemuel.xr.tts.application.port.out.TtsJobQueuePort
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
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

    override fun submit(jobId: String, task: () -> Unit): Boolean =
        try {
            executor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    // 워커 스레드에서 예외가 새어나가면 스레드만 죽고 아무도 모른다.
                    // 작업 자체의 실패 처리(FAILED 기록)는 task 안에서 하고, 여기는 최후 방어선이다.
                    log.error("TTS job {} 최후 방어선에서 잡힘", jobId, e)
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            log.warn("TTS 큐 포화 — job {} 거절 (depth={})", jobId, queue.size)
            false
        }

    override fun queueDepth(): Int = queue.size

    @PreDestroy
    fun shutdown() {
        // 진행 중인 합성은 몇 분이 걸릴 수 있다. 종료를 무한정 기다리지 않는다 —
        // 어차피 결과는 캐시 행에 남고, 못 끝낸 건 PENDING 으로 남아 재요청 시 재시도된다.
        executor.shutdownNow()
    }
}
