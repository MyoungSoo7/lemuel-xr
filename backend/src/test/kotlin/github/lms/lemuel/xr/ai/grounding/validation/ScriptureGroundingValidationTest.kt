package github.lms.lemuel.xr.ai.grounding.validation

import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase
import github.lms.lemuel.xr.ai.grounding.eval.EvalMetrics
import github.lms.lemuel.xr.ai.grounding.eval.GroundingDataset
import github.lms.lemuel.xr.ai.grounding.eval.GroundingScorer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * 라이브 **회귀** 게이트 — 고정된(pinned) 정책에서 골든셋 `signed_off` 표본이 전부 기대 상태와
 * 맞는지 확인한다. 진짜 Gemini 임베딩을 쓰므로 `GEMINI_API_KEY` 없으면 자동 비활성화된다.
 *
 * 역할 분담(스펙 §7):
 *  - **이 테스트** = 회귀. "지금 쓰는 임계치가 아직 유효한가" 를 예/아니오로 답한다.
 *  - [github.lms.lemuel.xr.ai.grounding.eval.GroundingThresholdSweepReport] = 탐색.
 *    임계치 격자를 훑어 precision/recall 곡선과 P3 만족 구간을 리포트로 낸다.
 *
 * 픽스처·정책은 더 이상 이 파일에 하드코딩되지 않는다. 리포 루트 `eval/grounding/v1/` 이
 * 단일 출처이고, 무결성은 `GroundingDatasetTest` 가 네트워크 없이 CI 에서 지킨다.
 */
@Tag("live-embedding")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ScriptureGroundingValidationTest {

    @Test
    fun `real embeddings separate orthodox from heterodox meditations`() {
        val dataset = GroundingDataset.load()
        val policy = dataset.manifest.pinnedPolicy.toPolicy()
        val useCase = EvaluateGroundingUseCase(
            GeminiEmbeddingAdapter(apiKey = System.getenv("GEMINI_API_KEY"), model = "gemini-embedding-001"),
            GroundingScorer.NOOP_METRICS,
        )

        // draft 는 사람 사인오프 전이라 게이트 판단 근거가 될 수 없다 — 회귀는 signed_off 만 본다.
        val fixtures = dataset.signedOff
        val outcomes = GroundingScorer.outcomes(useCase, fixtures, policy)
        val summary = EvalMetrics.summarize(outcomes)
        val b = summary.binary

        val report = buildString {
            append("\n=== grounding validation (${dataset.manifest.dataset} ${dataset.manifest.version}, ")
            append("sim=${policy.similarityThreshold} maxUnsup=${policy.maxUnsupportedRate}) ===\n")
            outcomes.sortedBy { it.id }.forEach { o ->
                append(
                    "%-34s expected=%-12s got=%-12s rate=%.2f %s\n".format(
                        o.id, o.expected, o.actual, o.unsupportedRate, if (o.correct) "OK" else "MISS",
                    ),
                )
            }
            append("--\n")
            append("n=%d  TP=%d FP=%d FN=%d TN=%d  기권=%d\n".format(summary.sampleCount, b.tp, b.fp, b.fn, b.tn, b.abstained))
            append(
                "정밀도=%s 재현율=%s F1=%s  P3(오탐률=FP/(TP+FP))=%s\n".format(
                    b.precision.fmt(), b.recall.fmt(), b.f1.fmt(), b.p3FalseRejectRate.fmt(),
                ),
            )
        }
        println(report)

        // 기권이 섞이면 위 지표를 믿을 수 없다(임베딩 장애를 정밀도 1.0 으로 오독하게 됨).
        assertThat(b.abstained)
            .withFailMessage("기대가 ACCEPTED/REJECTED 인데 판정 불가 — 임베딩 호출 실패 의심:%s", report)
            .isZero()
        // 고정 임계치에서 signed_off 전부 일치해야 한다. 깨지면 재튜닝 후 스윕 리포트 경로를
        // 커밋 메시지에 남기는 것이 규약(eval/grounding/README.md §5).
        assertThat(summary.mismatches)
            .withFailMessage("고정 임계치에서 불일치 발생 — 스윕 리포트로 재튜닝 필요:%s", report)
            .isEmpty()
    }

    private fun Double?.fmt(): String = this?.let { "%.3f".format(it) } ?: "n/a"
}
