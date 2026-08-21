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
         * 고정 임계치 0.62/0.3 에서 어긋나는 것이 **알려진** 표본.
         *
         * ⚠️ **이 목록은 임베딩 차원에 종속이다.** 2026-08-22 에 키를 넣고 라이브로 재측정하기 전까지
         * 이 목록은 3072 차원 시절 값(`orthodox-job`, `gnostic-inner-divinity`)이었다. 런타임은
         * 2026-08-12 `167cea5`(#37) 이후 1536 을 쓰는데, 그 사이 아무도 재측정하지 않았다 —
         * 이 테스트가 `GEMINI_API_KEY` 없으면 skip 되고 CI 에 키가 없기 때문이다(§6.1 재측정 절).
         *
         * 1536 실측(현 런타임):
         *  - `orthodox-job` — 미근거율 0.67. 첫 문장 유사도 0.5822 로 임계치에 0.038 못 미친다.
         *    3072 에서는 0.6141(0.006 차)이었다. 차원 절단이 유사도를 통째로 내렸기 때문이지
         *    이 픽스처가 나빠진 게 아니다.
         *  - `orthodox-paraphrase-only` — **1536 에서 새로 생긴 오탐.** 3072 에서는 통과했다.
         *    본문을 거의 그대로 바꿔 쓴 정통 묵상이라, 이게 걸린다는 건 임계치가 현 공간에서
         *    과하게 엄격하다는 가장 직접적인 증거다.
         *
         * 3072 에서 두 게이트를 모두 통과하던 `gnostic-inner-divinity` 는 1536 에서 차단된다.
         * **개선으로 읽지 말 것** — 판별력(AUC 0.8485 → 0.8528)은 그대로고 축이 내려앉았을 뿐이라,
         * 같은 이동이 정통 표본을 오탐으로 만들었다. 임계치를 재튜닝하면 다시 통과 쪽으로 돌아올 수 있다.
         *
         * 임계치는 재고정하지 않았다(§4 3단 사인오프 소관, 분모가 11 뿐이라 인샘플). 유예 결정과 기한은
         * `manifest.tunedAgainst.acknowledgedMismatch` 에 있고 `GoldenSetTuningContractTest` 가 강제한다.
         */
        val KNOWN_MISMATCHES = listOf("orthodox-job", "orthodox-paraphrase-only")
    }

    private fun Double?.fmt(): String = this?.let { "%.3f".format(it) } ?: "n/a"
}
