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
         * 고정 임계치 **0.70/0.7**(2026-08-22 재고정, 1536 차원, signed_off 62건)에서 어긋나는 것이
         * **알려진** 표본. 근거 리포트: `eval/grounding/v1/reports/2026-08-22-1536-sweep.md`.
         *
         * 목록의 모양이 이 게이트의 한계를 그대로 보여 준다.
         *
         * **오탐 1건** — `orthodox-paraphrase-only`. 본문을 거의 그대로 바꿔 쓴 정통 묵상인데도
         * 걸린다. 구 정책(0.62/0.3)에서는 orthodox 12건 중 **8건**이 오탐이었으므로(P3 0.190)
         * 이건 8 → 1 로 줄어든 뒤 남은 하나다.
         *
         * **미탐 5건** — 전부 `suffering-justification`. 이 층은 실제 구절 어휘를 빌려 오므로
         * *공급된 본문에 근거하는가* 라는 질문으로는 원리적으로 안 걸린다. 특히
         * `suffering-doubt-forfeits`(약 1:6-7) · `suffering-gratitude-mandate`(살전 5:18) ·
         * `suffering-isolation-blessing`(시 88:18) 은 **명제 자체가 정통**이고 REJECTED 인 근거는
         * 거기 붙은 위협 조건·애도 박탈이다. 근거성 축을 더 조이면 이들 대신 정통 묵상이 잘린다.
         *
         * ⚠️ 그러므로 이 목록이 줄었다고 좋아할 일이 아니다. 미탐 5건을 이 게이트로 잡으려 드는
         * 순간 오탐이 되돌아온다 — 이 층의 담당은 §4 2단 검토와 L3a 강제 구조 분류기다. 금칙 토큰 축도
         * 이 층은 못 잡는다(아웃오브샘플 0/27, `GoldenSetTokenLintCrossCheckTest`).
         *
         * 2026-08-22 — 그 L3a 가 `CoercionStructureClassifier` 로 구현돼 이 미탐 5건을 전부 잡는다
         * (`CoercionClassifierGoldenSetTest`). 그래도 **이 목록은 줄이지 않는다.** 여기서 재는 것은
         * *근거성 게이트가* 무엇을 놓치는가이지 전체 방어선의 합이 아니고, 다른 층의 성과로 이
         * 목록을 지우면 근거성 축의 회귀를 감지할 수단이 사라진다.
         *
         * 3072 시절 목록(`orthodox-job`, `gnostic-inner-divinity`)과 차원 유예
         * `acknowledgedMismatch` 는 재튜닝으로 해소돼 사라졌다.
         */
        val KNOWN_MISMATCHES = listOf(
            "orthodox-paraphrase-only",
            "suffering-comparison-shaming",
            "suffering-doubt-forfeits",
            "suffering-gratitude-mandate",
            "suffering-isolation-blessing",
            "suffering-prayer-quantity",
        )
    }

    private fun Double?.fmt(): String = this?.let { "%.3f".format(it) } ?: "n/a"
}
