package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetEvalMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.eval.EvalMetrics
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * 잡의 계약은 하나다: **무슨 일이 있어도 스케줄을 죽이지 않는다.**
 * 예외가 스케줄러 스레드까지 올라가면 다음 실행까지 통째로 잃고, 그러면 지표가 아니라
 * 지표의 부재만 남는다.
 */
class GoldenSetEvaluationJobTest {

    private val evaluate: EvaluateGoldenSetUseCase = mock()
    private val metrics: GoldenSetEvalMetricsPort = mock()
    private val job = GoldenSetEvaluationJob(evaluate, metrics)

    private fun result(mismatches: List<String>) = EvaluateGoldenSetUseCase.Result(
        version = "v1",
        policy = GroundingPolicy(0.62, 0.3),
        summary = EvalMetrics.Summary(
            sampleCount = 7,
            binary = EvalMetrics.summarize(emptyList()).binary,
            perClass = emptyList(),
            mismatches = mismatches,
        ),
        outcomes = emptyList(),
        embeddedTexts = 11,
        excludedDrafts = 6,
    )

    @Test
    fun `성공하면 결과를 발행한다`() {
        val r = result(emptyList())
        evaluate.stub { on { run(null) } doReturn r }

        job.run()

        verify(metrics).publish(r)
        verify(metrics, never()).failed()
    }

    @Test
    fun `불일치가 있어도 실패로 처리하지 않고 값을 낸다`() {
        // 임계치 판단은 알람 규칙의 몫이다. 여기서 던지면 정작 판단할 값이 안 나간다.
        val r = result(listOf("gnostic-01"))
        evaluate.stub { on { run(null) } doReturn r }

        assertThatCode { job.run() }.doesNotThrowAnyException()

        verify(metrics).publish(r)
        verify(metrics, never()).failed()
    }

    @Test
    fun `채점이 실패해도 예외를 삼키고 실패만 센다`() {
        evaluate.stub { on { run(null) } doThrow RuntimeException("embedding backend down") }

        assertThatCode { job.run() }.doesNotThrowAnyException()

        verify(metrics).failed()
        verify(metrics, never()).publish(any())
    }
}
