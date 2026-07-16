package github.lms.lemuel.xr.recovery.application

import github.lms.lemuel.xr.recovery.application.port.out.RecoveryMetricPort
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

/**
 * recovery/application 스켈레톤 잡 스모크 테스트 — run() 이 예외 없이 완료되는지.
 *
 * 현재 run() 은 TODO 스켈레톤(로그만)이라 포트를 건드리지 않는다. 실제 집계가
 * 구현되면 여기서 save 상호작용을 검증하도록 확장한다.
 */
class ComputeDailyMetricsJobTest {

    private val metrics: RecoveryMetricPort = mock()

    @Test
    fun `run 예외없이 완료 그리고 포트 미접근`() {
        val job = ComputeDailyMetricsJob(metrics)

        assertThatCode { job.run() }.doesNotThrowAnyException()

        // 스켈레톤 단계 — 아직 aggregate/save 없음(no-op).
        verifyNoInteractions(metrics)
    }
}
