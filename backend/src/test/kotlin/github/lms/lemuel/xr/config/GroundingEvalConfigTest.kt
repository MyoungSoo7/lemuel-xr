package github.lms.lemuel.xr.config

import com.fasterxml.jackson.databind.ObjectMapper
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGoldenSetUseCase
import github.lms.lemuel.xr.ai.grounding.application.GoldenSetEvaluationJob
import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetEvalMetricsPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetPort
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 배선의 **기본값이 안전한지**를 확인한다.
 *
 * 이 기능을 켜면 유료 임베딩 API 를 주기 호출한다. 그래서 "배포하면 켜진다" 가 아니라
 * 배포자가 명시해야 켜지는 쪽이어야 하고, 켜졌는데 키가 없으면 매 주기 실패가 쌓이는 대신
 * 부팅에서 바로 드러나야 한다.
 */
class GroundingEvalConfigTest {

    @Configuration(proxyBeanMethods = false)
    class Supporting {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(Supporting::class.java, GroundingEvalConfig::class.java)

    @Test
    fun `기본값은 꺼짐이라 빈이 하나도 생기지 않는다`() {
        runner.run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).doesNotHaveBean(GoldenSetEvaluationJob::class.java)
            assertThat(ctx).doesNotHaveBean(EvaluateGoldenSetUseCase::class.java)
            assertThat(ctx).doesNotHaveBean(EmbeddingPort::class.java)
        }
    }

    @Test
    fun `명시적으로 꺼도 켜지지 않는다`() {
        runner.withPropertyValues("grounding.eval.enabled=false").run { ctx ->
            assertThat(ctx).doesNotHaveBean(GoldenSetEvaluationJob::class.java)
        }
    }

    @Test
    fun `켜면 잡까지 전부 배선된다`() {
        runner
            .withPropertyValues(
                "grounding.eval.enabled=true",
                "grounding.eval.api-key=test-key",
                "grounding.eval.version=v1",
            )
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).hasSingleBean(GoldenSetPort::class.java)
                assertThat(ctx).hasSingleBean(EmbeddingPort::class.java)
                assertThat(ctx).hasSingleBean(GoldenSetEvalMetricsPort::class.java)
                assertThat(ctx).hasSingleBean(EvaluateGoldenSetUseCase::class.java)
                assertThat(ctx).hasSingleBean(GoldenSetEvaluationJob::class.java)
                assertThat(ctx.getBean(GroundingEvalProperties::class.java).version).isEqualTo("v1")
            }
    }

    @Test
    fun `켰는데 키가 없으면 부팅에서 실패한다`() {
        // 조용히 켜지면 매일 03시 30분에 실패만 쌓이고, 아무도 값이 없다는 걸 눈치채지 못한다.
        runner.withPropertyValues("grounding.eval.enabled=true").run { ctx ->
            assertThat(ctx).hasFailed()
            assertThat(ctx.startupFailure).rootCause()
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("GEMINI_API_KEY")
        }
    }

    @Test
    fun `설정 기본값은 하루 1회 03시 30분이다`() {
        val props = GroundingEvalProperties()
        assertThat(props.enabled).isFalse()
        assertThat(props.cron).isEqualTo("0 30 3 * * *")
        assertThat(props.embeddingModel).isEqualTo("gemini-embedding-001")
        assertThat(props.apiKey).isEmpty()
    }

    @Test
    fun `ObjectMapper 를 주입받아 골든셋을 읽는다`() {
        // 어댑터가 자체 mapper 를 만들면 애플리케이션의 직렬화 설정과 갈라진다.
        runner
            .withPropertyValues("grounding.eval.enabled=true", "grounding.eval.api-key=test-key")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ObjectMapper::class.java)
                assertThat(ctx.getBean(GoldenSetPort::class.java).load("v1").fixtures).isNotEmpty()
            }
    }
}
