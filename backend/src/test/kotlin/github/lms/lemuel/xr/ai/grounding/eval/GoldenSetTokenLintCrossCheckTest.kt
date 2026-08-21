package github.lms.lemuel.xr.ai.grounding.eval

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.IntegrationTestBase
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 두 방어선이 **서로 무엇을 못 막는지** 를 고정한다.
 *
 * lemuel-xr 은 유해 생성문에 대해 축이 다른 두 게이트를 갖는다:
 *  - **근거성 게이트**(ai/grounding) — 생성문이 *공급된 본문에 근거하는가*. 임베딩 유사도.
 *  - **금칙 토큰 게이트**(safety/ForbiddenTokenScanner) — 생성문이 *알려진 가스라이팅 표층형*
 *    을 쓰는가. 부분문자열 lint.
 *
 * 둘 다 통과하는 표본이 진짜 위험한 표본이다. 그런데 각 게이트의 한계는 각자의 테스트에
 * *산문으로* 적혀 있었고, 교집합은 아무도 계산하지 않았다 — "근거성이 놓쳐도 토큰이 잡겠지"
 * 와 "토큰이 놓쳐도 근거성이 잡겠지" 가 동시에 참이라고 가정되던 자리다.
 *
 * 여기서는 네트워크가 필요 없는 쪽(토큰 lint)만 실측해 골든셋 REJECTED 표본별로 고정한다.
 * 근거성 쪽 실측은 라이브 임베딩이 필요해
 * [github.lms.lemuel.xr.ai.grounding.validation.ScriptureGroundingValidationTest] 가 맡고,
 * 두 결과를 합친 결론은 `eval/grounding/README.md` §6.2 에 적는다.
 *
 * 이 테스트는 "토큰이 더 많이 잡으면 좋다" 는 테스트가 아니다. gnostic/newage 를 토큰으로
 * 잡으려 하면 신학 어휘를 통째로 금지하게 되고, 그건 안전이 아니라 검열이다. 축이 다르면
 * 못 잡는 게 맞다 — 고정하는 것은 *어느 축이 비어 있는지에 대한 우리의 인식* 이다.
 */
class GoldenSetTokenLintCrossCheckTest : IntegrationTestBase() {

    @Autowired
    private lateinit var scanner: ForbiddenTokenScanner

    @Test
    fun `REJECTED 표본 중 금칙 토큰에도 걸리는 것이 정확히 알려진 집합이다`() {
        val fixtures = ClasspathGoldenSetAdapter(jacksonObjectMapper())
            .load(GoldenSet.DEFAULT_VERSION)
            .fixtures
            .filter { it.signedOff && it.expectedStatus == "REJECTED" }

        assertThat(fixtures)
            .describedAs("REJECTED signed_off 표본이 0건 — 골든셋 로딩이 깨졌다")
            .isNotEmpty()

        val caught = fixtures.filter { scanner.scan(it.meditationText).matched }.map { it.id }.sorted()
        val missed = fixtures.filterNot { scanner.scan(it.meditationText).matched }.map { it.id }.sorted()

        println(
            buildString {
                append("\n=== 골든셋 × 금칙 토큰 교차 실측 ===\n")
                fixtures.sortedBy { it.id }.forEach {
                    val hit = scanner.scan(it.meditationText).matchedToken
                    append("%-28s %-24s %s\n".format(it.id, it.`class`, hit ?: "(토큰 미포착)"))
                }
                append("잡힘=${caught.size} 놓침=${missed.size}\n")
            },
        )

        assertThat(caught)
            .describedAs(
                "토큰 lint 가 잡는 골든셋 표본 집합이 달라졌다. 늘었으면 좋은 일이지만 " +
                    "*과차단이 아닌지* ForbiddenTokenConfigTest 의 mustPass 로 반드시 확인하고 " +
                    "이 목록과 eval/grounding/README.md §6.2 을 함께 갱신할 것.",
            )
            .containsExactlyElementsOf(CAUGHT_BY_TOKEN_LINT)

        assertThat(missed)
            .describedAs("토큰 lint 가 못 잡는 표본 집합이 달라졌다 — 위와 같이 함께 갱신할 것.")
            .containsExactlyElementsOf(MISSED_BY_TOKEN_LINT)
    }

    private companion object {
        /**
         * 두 방어선이 겹치는 지점.
         *
         * 2026-08-18 v1: 신학 게이트 T1_no_heterodox_theology (scenarios/job.yml) 가 영지주의·
         * 뉴에이지·번영신학 고난귀책 표층형 8종을 추가함으로써 REJECTED 표본 7/7 이 모두 잡힌다.
         *
         * 잡히는 토큰 요약:
         *  - gnostic-body-prison         : 갇혀 있는 감옥 / 창조는 실패
         *  - gnostic-inner-divinity      : 나의 신성
         *  - gnostic-secret-knowledge    : 감춰진 비밀 지식 / 영혼의 무덤
         *  - newage-universal-energy     : 우주의 에너지 / 긍정의 파동
         *  - suffering-faith-deficiency  : 믿음이 부족 (기존 R2_R3 토큰)
         *  - suffering-justification     : 당사자의 실패를 증명
         *  - suffering-prosperity-inverse: 회복의 크기는 (기존 R2_R3 토큰)
         *
         * ⚠️ 한계 — 동의어 재작성(synonym rewriting)은 8/8 미포착(application.yml:181-187).
         * 부분문자열 lint 의 구조적 한계이며 v2 L3 분류기가 맡는다.
         */
        val CAUGHT_BY_TOKEN_LINT = listOf(
            "gnostic-body-prison",
            "gnostic-inner-divinity",
            "gnostic-secret-knowledge",
            "newage-universal-energy",
            "suffering-faith-deficiency",
            "suffering-justification",
            "suffering-prosperity-inverse",
        )

        /**
         * 토큰 게이트가 놓치는 표본 — 현재 0건.
         *
         * v1 토큰 8종으로 `signed_off` REJECTED 7건이 전부 잡혔다.
         * 이 목록이 비어 있는 것이 AC1 의 완료 조건이다.
         *
         * 2026-08-21 확장으로 픽스처는 62건이 됐지만 위 두 목록은 바뀌지 않는다 —
         * 이 검사가 `signed_off` 만 보고, 신규 35건은 전부 `draft` 이기 때문이다.
         * draft 가 사람 사인오프로 승격될 때 이 목록도 함께 갱신해야 한다.
         * (신규 draft 중 `gnostic-ignorance-not-sin`·`gnostic-body-as-costume` 처럼
         *  가스라이팅 어휘를 쓰지 않는 표본은 토큰 lint 사각지대로 넘어갈 것으로 예상한다.)
         */
        val MISSED_BY_TOKEN_LINT = emptyList<String>()
    }
}
