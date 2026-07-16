package github.lms.lemuel.xr.recovery.application

import github.lms.lemuel.xr.recovery.application.port.out.RecoveryMetricPort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 매일 04:00 KST 회복 지표 일별 집계.
 *
 * 현재는 스켈레톤. 실제 구현은:
 * - 전일 emotion_logs aggregate (count distinct emotion, avg intensity)
 * - diary_entries word_count 합산
 * - game_sessions completed 개수
 * - safety_alerts severity high+ 개수 → risk_signal_count
 * - TF-IDF 키워드 top 5 (Phase 2)
 *
 * 집계 결과는 [github.lms.lemuel.xr.recovery.domain.RecoveryMetric.newMetric] 로 만들어
 * [RecoveryMetricPort.save] 로 쓴다 (JPA 엔티티가 아닌 순수 도메인 타입).
 */
@Component
class ComputeDailyMetricsJob(
    private val metrics: RecoveryMetricPort,
) {

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "xr-compute-daily-metrics", lockAtMostFor = "PT30M")
    fun run() {
        log.info("recovery_metrics 일별 집계 시작")
        // TODO: implement aggregation
        log.info("recovery_metrics 일별 집계 완료")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ComputeDailyMetricsJob::class.java)
    }
}
