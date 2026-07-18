package github.lms.lemuel.xr.ai.grounding.validation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * 라이브 검증 harness — 진짜 Gemini 임베딩으로 5 픽스처를 돌려 게이트의 의미적 판별력을 입증.
 * GEMINI_API_KEY 없으면 자동 비활성화(@EnabledIfEnvironmentVariable). CI 기본 제외용 @Tag.
 *
 * 임계치는 실행 시 튜닝 대상. 아래 정책으로 정통 ACCEPTED / 적대적 REJECTED 분리가 안 되면,
 * 스펙 §7 에 따라 이 테스트를 임계치 스윕 리포트로 바꾸고 precision/recall 을 기록한다.
 */
@Tag("live-embedding")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ScriptureGroundingValidationTest {

    private data class Fixture(
        val purpose: String = "meditation",
        val expectedStatus: String = "",
        val meditationText: String = "",
        val passages: List<Passage> = emptyList(),
    )

    private val mapper = jacksonObjectMapper()
    private val noopMetrics = object : GroundingMetricsPort {
        override fun evaluated(purpose: String) {}
        override fun rejected(purpose: String) {}
        override fun unsupportedRate(purpose: String, rate: Double) {}
        override fun inconclusive(purpose: String) {}
    }

    // 2026-07-19 라이브 튜닝(gemini-embedding-001, 3072차원) 결과 도출한 임계치.
    // 관측 per-fixture 미근거율: 정통 욥/시88 = 0.00, 영지주의/뉴에이지 = 1.00,
    // 고난정당화 = 0.33(스펙 §8 "근거 있는 오독" — 실구절 어휘를 빌려 2/3 문장이 근거로 잡힘).
    // maxUnsupportedRate 0.5 로는 고난정당화가 통과(MISS) → 0.3 으로 조이면 5/5 분리.
    // ⚠️ n=5 소표본 튜닝이라 과적합 주의. 고난정당화 유형은 근본적으로 theology-reviewer 가
    //    잡아야 하는 케이스(§8). 임베딩 모델 변경 시 재튜닝 필요.
    private val policy = GroundingPolicy(similarityThreshold = 0.62, maxUnsupportedRate = 0.3)

    private val fixtureNames = listOf(
        "orthodox-job", "psalm88-lament",
        "gnostic-secret-knowledge", "newage-universal-energy", "suffering-justification",
    )

    private fun load(name: String): Fixture =
        javaClass.getResourceAsStream("/grounding/$name.json").use {
            mapper.readValue(it, Fixture::class.java)
        }

    @Test
    fun `real embeddings separate orthodox from heterodox meditations`() {
        val useCase = EvaluateGroundingUseCase(
            GeminiEmbeddingAdapter(apiKey = System.getenv("GEMINI_API_KEY"), model = "gemini-embedding-001"),
            noopMetrics,
        )
        val report = StringBuilder("\n=== grounding validation ===\n")
        var failures = 0
        fixtureNames.forEach { name ->
            val fx = load(name)
            val verdict = useCase.evaluate(fx.purpose, fx.meditationText, fx.passages, policy)
            val expected = GroundingStatus.valueOf(fx.expectedStatus)
            val ok = verdict.status == expected
            if (!ok) failures++
            report.append(
                "%-28s expected=%-10s got=%-12s rate=%.2f %s\n".format(
                    name, expected, verdict.status, verdict.unsupportedRate, if (ok) "OK" else "MISS",
                ),
            )
        }
        println(report)
        // 스펙 §7: 문서화된 임계치에서 전부 기대 상태와 일치해야 한다.
        assertThat(failures)
            .withFailMessage("일부 픽스처가 기대 상태와 불일치 — 임계치 튜닝 또는 precision/recall 리포트로 전환 필요:%s", report)
            .isZero()
    }
}
