package github.lms.lemuel.xr.ai.grounding.validation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGoldenSetUseCase
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
 * 단일 출처이고, 무결성은 `GoldenSetIntegrityTest` 가 네트워크 없이 CI 에서 지킨다.
 */
@Tag("live-embedding")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ScriptureGroundingValidationTest {

    @Test
    fun `real embeddings separate orthodox from heterodox meditations`() {
        // 프로덕션이 매일 돌리는 바로 그 use-case 를 그대로 쓴다 — 배선까지 함께 회귀 검증된다.
        // (draft 제외·고정 정책 선택·합성 트래픽 메트릭 격리가 전부 use-case 안에 있다.)
        val result = EvaluateGoldenSetUseCase(
            GeminiEmbeddingAdapter(apiKey = System.getenv("GEMINI_API_KEY"), model = "gemini-embedding-001"),
            ClasspathGoldenSetAdapter(jacksonObjectMapper()),
        ).run()
        val policy = result.policy
        val outcomes = result.outcomes
        val summary = result.summary
        val b = summary.binary

        val report = buildString {
            append("\n=== grounding validation (golden set ${result.version}, ")
            append("sim=${policy.similarityThreshold} maxUnsup=${policy.maxUnsupportedRate}) ===\n")
            outcomes.sortedBy { it.id }.forEach { o ->
                append(
                    "%-34s expected=%-12s got=%-12s rate=%.2f %s\n".format(
                        o.id, o.expected, o.actual, o.unsupportedRate, if (o.correct) "OK" else "MISS",
                    ),
                )
            }
            append("--\n")
            append("draft 제외=%d\n".format(result.excludedDrafts))
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
        // 고정 임계치에서 불일치 집합이 **정확히** 알려진 2건이어야 한다(README §6.1).
        //
        // `isEmpty()` 가 아닌 이유: 2026-08-04 성경 본문 교정 이후 이 둘은 실제로 어긋나 있고,
        // 표본이 목표치에 못 미쳐 임계치를 재고정하지 않기로 했다. 빨간불로 방치하면 아무도 안 보게 된다.
        // `containsExactly` 인 이유: 목록이 늘어나면(새 회귀) 물론이고 **줄어들어도**(누가 고쳤는데
        // 기록을 안 남김) 실패한다. 허용 목록이 조용히 자라는 것을 막는 유일한 방법이다.
        assertThat(summary.mismatches)
            .withFailMessage(
                "고정 임계치의 불일치 집합이 알려진 상태와 다르다. 늘었으면 회귀, 줄었으면 " +
                    "README §6.1 과 이 목록을 함께 갱신할 것:%s",
                report,
            )
            .containsExactlyInAnyOrderElementsOf(KNOWN_MISMATCHES)
    }

    private companion object {
        /**
         * 고정 임계치 0.62/0.3 에서 어긋나는 것이 **알려진** 표본. 성격이 서로 다르다:
         *  - `orthodox-job` — 유사도 0.6141 로 임계치에 0.006 못 미치는 오탐. 표본이 늘면 재판단한다.
         *  - `gnostic-inner-divinity` — 이단인데 통과한다. 임계치가 아니라 유사도 기반 근거성의 구조적 한계다.
         */
        val KNOWN_MISMATCHES = listOf("orthodox-job", "gnostic-inner-divinity")
    }

    private fun Double?.fmt(): String = this?.let { "%.3f".format(it) } ?: "n/a"
}
