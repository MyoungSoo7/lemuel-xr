package github.lms.lemuel.xr.safety.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * **L3a 가 못 잡는 것을 초록불로 고정한다.**
 *
 * ⚠️ 아래 문자열은 *의도적으로 만든 가스라이팅 표본* 이다. 사용자에게 노출하거나 LLM 프롬프트의
 * 예시로 쓰면 안 된다 — 판정 입력으로만 쓴다. 골든셋 manifest 의 `contentWarning` 과 같은 이유다.
 *
 * ## 왜 실패를 assert 하는가
 * `docs/MVP-JOB.md` §12.4 는 금지 토큰 게이트의 보증 한계를 이렇게 적어 두었다 —
 * "토큰은 표층형 lint 이지 의미 분석이 아니다. 초록은 「선언된 표층형이 없다」까지다."
 * L3a 도 똑같이 유한하다. **관계 lint 이지 의미 분석이 아니다.** 두 어휘군을 잇는 통사 결합을
 * 보므로 한쪽만 바꾼 유의어에는 견디지만, **양쪽을 동시에 갈아 쓰면 통과한다.**
 *
 * 그 한계를 문서에만 적으면 다음 사람은 초록불을 "강제 구조가 없다"로 읽는다. 그래서 실물
 * 반례를 테스트로 박아 둔다. 이 테스트가 **빨간불이 되면 좋은 소식이다** — 규칙이 그 반례까지
 * 덮게 됐다는 뜻이므로, 반례를 한 칸 더 어렵게 고쳐 다시 초록으로 만들고 무엇을 덮게 됐는지
 * 커밋 메시지에 남긴다. `GoldenSetTokenLintCrossCheckTest` 의 `MISSED_BY_TOKEN_LINT` 와 같은 계열이다.
 *
 * 이 층을 실제로 닫는 것은 L3b(LLM 자문)이며, L3b 는 판정을 뒤집지 않고 사람 검토 큐로만 나간다
 * (승격계약 §1.1: 판정은 결정론적이어야 하고 LLM 재판정으로 뒤집을 수 없다).
 */
class CoercionClassifierKnownGapTest {

    private val classifier = CoercionStructureClassifier()

    /**
     * `suffering-faith-deficiency` 와 **강제 구조가 같은** 문장이다. 원문은
     * "네가 아직 낫지 않은 것은 믿음이 부족하기 때문이다 …" 로 귀책(부족×때문이다)과
     * 반사실 공로 환산(다면×았을 것이다)이 걸린다. 아래는 두 어휘군을 *동시에* 갈아 쓴 것이다:
     * 부족→얕음, 때문이다→자리하고 있다, 았을 것이다→저물었으리라, 크기→길이.
     */
    private val synonymRewriteOfFaithDeficiency = listOf(
        "치유가 더딘 자리에는 늘 신뢰의 얕음이 함께 있다.",
        "온전히 신뢰했더라면 이 밤은 진작 저물었으리라.",
        "어둠이 머무는 길이는 곧 그 신뢰의 얕음을 재는 자다.",
    ).joinToString(" ")

    @Test
    fun `유의어 재작성은 L3a 를 통과한다 — 이것이 이 층의 보증 한계다`() {
        val verdict = classifier.classify(synonymRewriteOfFaithDeficiency)

        assertThat(verdict.coercive)
            .describedAs(
                "이 반례가 걸리기 시작했다면 규칙이 넓어진 것이다. 좋은 소식이므로 반례를 한 칸 더 " +
                    "어렵게 고쳐 다시 초록으로 만들고, 무엇을 새로 덮게 됐는지 커밋 메시지에 남길 것.",
            )
            .isFalse()
    }

    @Test
    fun `한쪽만 바꾼 유의어에는 견딘다 — 그래서 표층형 lint 보다 한 칸 위다`() {
        // 앞항(부족 어휘)만 바꾸고 뒤항(귀속 종결)은 그대로 둔 형태. 표층형 lint 라면
        // "믿음이 부족" 이라는 문자열이 사라진 순간 뚫리지만, 관계는 뒤항으로 여전히 닫힌다.
        val onlyLeftSwapped = "네가 아직 낫지 않은 것은 신뢰가 얕기 때문이다."
        assertThat(classifier.classify(onlyLeftSwapped).coercive)
            .describedAs("앞항만 바꿨는데 뚫렸다 — 관계가 아니라 구(句)를 보고 있다는 뜻이다")
            .isTrue()

        // 뒤항(귀속 종결)만 바꾼 형태. 이쪽이 관계의 **좁은 쪽**이라 처음 구현에서는 뚫렸고,
        // 한국어의 표준 귀속 구문("…에 있다")을 어휘군에 넣어 막았다. 표본의 자구를 좇은 게
        // 아니라 구문을 덮은 것이라 과적합이 아니다 — 그 판단의 근거는 이 문장이 골든셋에
        // 없는, 여기서 새로 지은 문장이라는 데 있다.
        val onlyRightSwapped = "네가 아직 낫지 않은 까닭은 믿음이 부족한 데 있다."
        assertThat(classifier.classify(onlyRightSwapped).coercive)
            .describedAs("뒤항만 바꿨는데 뚫렸다 — 귀속 종결 목록이 좁다는 뜻이다")
            .isTrue()
    }

    @Test
    fun `정통 명제를 그대로 인용하는 것만으로는 걸리지 않는다`() {
        // L3 의 존재 이유 자체 — 명제가 아니라 구조를 본다. 아래 셋은 골든셋의 미탐 3건이
        // 인용한 바로 그 구절들이고, 명제 자체는 전부 정통이다(약 1:6-7 · 살전 5:18 · 시 88:18).
        listOf(
            "오직 믿음으로 구하고 조금도 의심하지 말라.",
            "범사에 감사하라 이것이 그리스도 예수 안에서 너희를 향하신 하나님의 뜻이니라.",
            "주는 나의 사랑하는 자와 친구를 내게서 멀리 떠나게 하시며 나의 아는 자를 흑암에 두셨나이다.",
        ).forEach { proposition ->
            assertThat(classifier.classify(proposition).coercive)
                .describedAs("정통 명제 자체가 걸렸다: $proposition — L3 가 명제를 자르기 시작하면 존재 이유가 사라진다")
                .isFalse()
        }
    }
}
