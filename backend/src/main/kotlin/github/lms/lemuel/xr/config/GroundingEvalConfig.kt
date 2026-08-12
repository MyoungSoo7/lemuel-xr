package github.lms.lemuel.xr.config

import com.fasterxml.jackson.databind.ObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.adapter.out.metrics.MicrometerGoldenSetEvalMetricsAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGoldenSetUseCase
import github.lms.lemuel.xr.ai.grounding.application.GoldenSetEvaluationJob
import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetEvalMetricsPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetPort
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 골든셋 정기 채점 배선. **기본 비활성**(`grounding.eval.enabled=false`).
 *
 * 켜면 유료 임베딩 API 를 주기적으로 호출한다. 그래서 "설치하면 켜진다" 가 아니라
 * 배포자가 값을 명시해 켜는 방식이다. 꺼져 있으면 빈이 하나도 만들어지지 않으므로
 * 기존 동작에 영향이 0 이다.
 */
@Configuration
@EnableConfigurationProperties(GroundingEvalProperties::class)
@ConditionalOnProperty(prefix = "grounding.eval", name = ["enabled"], havingValue = "true")
class GroundingEvalConfig {

    @Bean
    fun goldenSetPort(mapper: ObjectMapper): GoldenSetPort = ClasspathGoldenSetAdapter(mapper)

    @Bean
    fun goldenSetEmbeddingPort(props: GroundingEvalProperties): EmbeddingPort {
        // 키 없이 켜면 매 주기 실패만 쌓인다. 부팅 때 바로 알려 주는 편이 낫다.
        require(props.apiKey.isNotBlank()) {
            "grounding.eval.enabled=true 인데 임베딩 API 키가 없다 — GEMINI_API_KEY 주입 필요"
        }
        return GeminiEmbeddingAdapter(
            apiKey = props.apiKey,
            model = props.embeddingModel,
            outputDimensionality = props.embeddingDimensions,
        )
    }

    @Bean
    fun goldenSetEvalMetricsPort(registry: MeterRegistry): GoldenSetEvalMetricsPort =
        MicrometerGoldenSetEvalMetricsAdapter(registry)

    @Bean
    fun evaluateGoldenSetUseCase(
        embeddings: EmbeddingPort,
        goldenSet: GoldenSetPort,
        props: GroundingEvalProperties,
    ): EvaluateGoldenSetUseCase = EvaluateGoldenSetUseCase(embeddings, goldenSet, props.version)

    @Bean
    fun goldenSetEvaluationJob(
        evaluate: EvaluateGoldenSetUseCase,
        metrics: GoldenSetEvalMetricsPort,
    ): GoldenSetEvaluationJob = GoldenSetEvaluationJob(evaluate, metrics)
}

/**
 * @property cron 기본 매일 03:30 KST. 하루 1회면 모델 드리프트 감지엔 충분하고 비용은 무시할 수준이다.
 * @property embeddingDimensions 임베딩 차원. 기본 1536 은 `scripture_embeddings.embedding vector(1536)`
 *   와 pgvector HNSW 의 2000 차원 상한에 맞춘 값이다. 자세한 근거는
 *   [GeminiEmbeddingAdapter] 문서 주석 참고. 이 값을 바꾸면 스키마도 함께 가야 한다.
 */
@ConfigurationProperties(prefix = "grounding.eval")
data class GroundingEvalProperties(
    val enabled: Boolean = false,
    val cron: String = "0 30 3 * * *",
    val version: String = GoldenSet.DEFAULT_VERSION,
    val embeddingModel: String = "gemini-embedding-001",
    val embeddingDimensions: Int = GeminiEmbeddingAdapter.DEFAULT_DIMENSIONS,
    val apiKey: String = "",
)
