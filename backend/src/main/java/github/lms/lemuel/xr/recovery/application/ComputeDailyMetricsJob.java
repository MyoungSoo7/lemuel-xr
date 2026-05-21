package github.lms.lemuel.xr.recovery.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:00 KST 회복 지표 일별 집계.
 *
 * <p>현재는 스켈레톤. 실제 구현은:
 * <ul>
 *   <li>전일 emotion_logs aggregate (count distinct emotion, avg intensity)</li>
 *   <li>diary_entries word_count 합산</li>
 *   <li>game_sessions completed 개수</li>
 *   <li>safety_alerts severity high+ 개수 → risk_signal_count</li>
 *   <li>TF-IDF 키워드 top 5 (Phase 2)</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComputeDailyMetricsJob {

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        log.info("recovery_metrics 일별 집계 시작");
        // TODO: implement aggregation
        log.info("recovery_metrics 일별 집계 완료");
    }
}
