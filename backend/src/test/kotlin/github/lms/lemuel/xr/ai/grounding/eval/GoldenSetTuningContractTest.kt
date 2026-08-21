package github.lms.lemuel.xr.ai.grounding.eval

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.IntegrationTestBase
import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment

/**
 * **골든셋의 임계치가 튜닝된 공간과 런타임이 실제로 쓰는 공간이 같은지** 를 고정한다.
 *
 * `manifest.json` 은 스스로 이렇게 선언한다:
 *
 * ```
 * "tunedAgainst": { "embeddingModel": "gemini-embedding-001", "dimensions": 3072, "tunedAt": "2026-07-19",
 *   "note": "... 임베딩 모델이 바뀌면 재튜닝 대상." }
 * ```
 *
 * 그런데 2026-08-12 `167cea5`(#37)가 어댑터를 3072 → 1536(MRL 절단 + L2 정규화)으로 바꿨다.
 * `scripture_embeddings.embedding vector(1536)` + pgvector HNSW 2000 차원 상한 때문에 **옳은 변경**이었지만,
 * manifest 가 요구한 "재튜닝" 은 일어나지 않았고 `pinnedPolicy` 0.62/0.3 은 3072 시절 값 그대로 남았다.
 *
 * 이게 10일간 조용했던 이유가 이 검사의 존재 이유다:
 *  - 그 요구사항은 **JSON 안의 산문**이었다. 읽는 코드가 없으니 아무것도 깨지지 않는다.
 *  - 실측하는 [github.lms.lemuel.xr.ai.grounding.validation.ScriptureGroundingValidationTest] 는
 *    `GEMINI_API_KEY` 가 없으면 skip 되고, **CI 에는 키가 없다.** 초록불은 "맞다" 가 아니라 "안 쟀다" 였다.
 *
 * 그래서 이 검사는 **네트워크도 키도 쓰지 않는다.** manifest 의 선언과 설정값을 대조할 뿐이라
 * 키 없는 CI 에서 항상 돌고, #37 이 들어온 날 빨간불이 났을 검사다.
 *
 * 2026-08-22 실측으로 확인한 차원 전환의 효과(리포트: `build/reports/grounding-eval/`):
 *  - 문장 154 쌍 중 137 개(89%)의 유사도가 **내려갔고 올라간 것은 0 개**다. 평균 −0.0288, 중앙값 −0.0330.
 *  - 판별력은 사실상 그대로다 — 문장 단위 AUC(정통 11 vs 이단 21) 3072 0.8485 → 1536 0.8528.
 *  - 즉 공간이 **좋아지거나 나빠진 게 아니라 축이 통째로 내려앉았다.** 3072 에 맞춘 0.62 는
 *    1536 에서 실질 ~0.649 처럼 동작한다(그래서 각 공간의 최적 F1 임계치도 0.64 → 0.60 으로 같은 폭만큼 내려간다).
 *  - 결과로 고정 정책의 P3 오탐률이 0.143 → 0.222 로 악화됐다. 정통 표본 4건이 새로 REJECTED 가 됐다.
 *
 * **이 검사는 임계치를 고쳐 주지 않는다.** 재튜닝은 n=11 signed_off 에 맞추는 인샘플 조정이 되기 쉬워
 * §4 3단 사인오프와 홀드아웃 없이 할 일이 아니다. 여기서 하는 일은 *불일치가 조용히 남지 못하게* 하는 것뿐이다.
 */
class GoldenSetTuningContractTest : IntegrationTestBase() {

    @Autowired
    private lateinit var env: Environment

    private val manifest by lazy {
        ClasspathGoldenSetAdapter(jacksonObjectMapper()).load(GoldenSet.DEFAULT_VERSION).manifest
    }

    @Test
    fun `골든셋 임계치가 튜닝된 차원과 런타임 설정 차원이 같다`() {
        val configured = env.getProperty("grounding.eval.embedding-dimensions", Int::class.java)

        assertThat(configured)
            .describedAs("grounding.eval.embedding-dimensions 가 설정에서 사라졌다 — application.yml 확인")
            .isNotNull()

        assertThat(manifest.tunedAgainst.dimensions)
            .describedAs(
                """
                골든셋 pinnedPolicy(${manifest.pinnedPolicy.similarityThreshold}/${manifest.pinnedPolicy.maxUnsupportedRate})
                가 튜닝된 차원(${manifest.tunedAgainst.dimensions}, tunedAt=${manifest.tunedAgainst.tunedAt})과
                런타임이 쓰는 차원($configured)이 다르다.

                차원을 바꾸면 유사도가 통째로 평행이동한다 — 2026-08-22 실측으로 3072→1536 은
                문장 154 쌍 중 137 개를 평균 -0.0288 만큼 끌어내렸고 올린 것은 0 개였다.
                판별력(AUC)은 그대로이므로 이건 '모델이 나빠졌다' 가 아니라 '임계치가 다른 좌표계의 값' 이라는 뜻이다.

                해야 할 일은 둘 중 하나다:
                 (a) 차원을 되돌린다 — 단, 1536 은 scripture_embeddings.embedding vector(1536) 과
                     pgvector HNSW 2000 차원 상한에 묶여 있으므로 스키마 마이그레이션이 함께 가야 한다.
                 (b) 새 차원에서 스윕을 다시 돌려 임계치를 재튜닝하고, manifest 의 tunedAgainst 를
                     그 값으로 갱신한다. manifest.rules 가 요구하는 대로 스윕 리포트 경로를 커밋 메시지에 남길 것.

                (b) 를 택할 때 주의: signed_off 는 현재 11건뿐이라 여기에 맞춘 임계치는 인샘플 값이다.
                §4 3단 사인오프 없이 이 숫자를 움직이면 '골든셋에만 맞는 게이트' 가 된다.
                """.trimIndent(),
            )
            .isEqualTo(configured)
    }

    @Test
    fun `골든셋 임계치가 튜닝된 모델과 런타임 설정 모델이 같다`() {
        val configured = env.getProperty("grounding.eval.embedding-model")

        assertThat(manifest.tunedAgainst.embeddingModel)
            .describedAs(
                "골든셋은 '${manifest.tunedAgainst.embeddingModel}' 로 튜닝됐는데 런타임은 '$configured' 를 쓴다. " +
                    "임베딩 모델이 다르면 유사도 스케일이 달라 pinnedPolicy 를 그대로 쓸 수 없다 — " +
                    "manifest.tunedAgainst.note 가 명시적으로 재튜닝을 요구하는 경우다.",
            )
            .isEqualTo(configured)
    }

    @Test
    fun `어댑터 기본 차원이 설정 기본값과 어긋나지 않는다`() {
        // 설정을 비우면 GroundingEvalProperties 가 이 상수로 떨어진다(GroundingEvalConfig:78).
        // 두 값이 갈라지면 "yml 을 지웠더니 다른 공간에서 채점되는" 조용한 경로가 생긴다.
        assertThat(env.getProperty("grounding.eval.embedding-dimensions", Int::class.java))
            .describedAs(
                "application.yml 의 grounding.eval.embedding-dimensions 와 " +
                    "GeminiEmbeddingAdapter.DEFAULT_DIMENSIONS 가 다르다. 설정이 비었을 때 조용히 다른 차원으로 채점된다.",
            )
            .isEqualTo(GeminiEmbeddingAdapter.DEFAULT_DIMENSIONS)
    }
}
