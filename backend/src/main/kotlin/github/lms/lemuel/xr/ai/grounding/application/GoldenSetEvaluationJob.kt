package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetEvalMetricsPort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * 골든셋 정기 채점 — 승격계약 §2 가 요구하는 "지속 측정" 의 실행 주체.
 *
 * 왜 CI 가 아니라 애플리케이션인가: CI 에는 임베딩 API 키가 없어 라이브 평가가 늘 skip 된다.
 * 값이 아예 생기지 않는 곳에 측정 체계를 두는 건 측정하는 시늉일 뿐이다. 반면 여기서 돌리면
 * **실제 배포된 모델·네트워크·설정 그대로** 재고, 이미 있는 ServiceMonitor 가 그대로 수집한다.
 *
 * 사용자 경로는 건드리지 않는다. 채점 대상은 고정 픽스처뿐이라 게이트를 실사용에 붙이기 전에도
 * 안전하게 관측할 수 있다.
 *
 * [SchedulerLock] — 롤링 업데이트로 옛/새 파드가 잠깐 공존할 때 양쪽이 동시에 임베딩 API 를
 * 태우는 것을 막는다.
 */
class GoldenSetEvaluationJob(
    private val evaluate: EvaluateGoldenSetUseCase,
    private val metrics: GoldenSetEvalMetricsPort,
) {

    @Scheduled(cron = "\${grounding.eval.cron}", zone = "Asia/Seoul")
    @SchedulerLock(name = "xr-grounding-goldenset-eval", lockAtMostFor = "PT15M")
    fun run() {
        try {
            val result = evaluate.run()
            metrics.publish(result)
            if (result.summary.mismatches.isNotEmpty()) {
                // 실패로 처리하지 않는다. 이 잡의 임무는 값을 내는 것이고, 임계치 판단은
                // 알람 규칙의 몫이다. 잡이 죽으면 그 값마저 사라진다.
                log.warn(
                    "골든셋 불일치 {}건: {} (정책 sim={}, maxUnsup={})",
                    result.summary.mismatches.size,
                    result.summary.mismatches,
                    result.policy.similarityThreshold,
                    result.policy.maxUnsupportedRate,
                )
            }
        } catch (e: Exception) {
            // 임베딩 API 장애로 스케줄러 스레드가 죽으면 다음 실행까지 통째로 잃는다.
            // 실패는 세고, 지난 성공 지표는 그대로 둔 채(마지막 성공 시각이 늙어 드러난다) 넘어간다.
            metrics.failed()
            log.error("골든셋 채점 실패: {}", e.message, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(GoldenSetEvaluationJob::class.java)
    }
}
