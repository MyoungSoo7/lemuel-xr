package github.lms.lemuel.xr.recovery.application;

import org.junit.jupiter.api.Test;

/**
 * recovery/application 스켈레톤 잡 스모크 테스트 — run() 이 예외 없이 완료되는지.
 */
class ComputeDailyMetricsJobTest {

    @Test
    void run_예외없이_완료() {
        new ComputeDailyMetricsJob().run();
    }
}
