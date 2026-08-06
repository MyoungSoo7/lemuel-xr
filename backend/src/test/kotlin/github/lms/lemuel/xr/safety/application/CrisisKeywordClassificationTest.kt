package github.lms.lemuel.xr.safety.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 위기 키워드 *분류* 검증.
 *
 * 기존 구현은 무엇이 매칭되든 matchedPattern="suicide_intent", severity="high" 를
 * 하드코딩했다. 즉 "자해"가 걸려도 감사 로그에는 자살 의도로 기록됐다 — 티어링이
 * 있는 척했지만 실제로는 없었고, 안전 도메인 데이터가 사실과 달랐다.
 *
 * 설계 원칙 — **보호 수준은 낮추지 않는다.**
 * 2026-08-06 에 3단 등급(critical/high/medium)이 들어왔지만, *등급 도입 이전부터 있던*
 * 키워드는 전부 critical 로 남는다. 등급 이전 런타임은 어떤 매칭이든 LLM 을 건너뛰고
 * 위기 화면을 강제했으므로, 그 동작에 해당하는 등급이 critical 이기 때문이다.
 * 등급 체계가 생겼다는 이유로 기존 키워드의 동작이 한 비트라도 바뀌면 그건 완화다.
 *
 * 하위호환: 이름 없는 기존 regex(SAFETY_CRISIS_REGEX 환경변수 override 포함)도
 * 계속 동작해야 한다. 그 경우 가장 보수적인 분류(critical)로 떨어진다.
 */
class CrisisKeywordClassificationTest {

    /** 명명 그룹을 쓰는 새 형식. */
    private val classified = CrisisKeywordScanner(
        "(?<suicideIntent>자살|죽고\\s?싶|죽어\\s?버|뛰어내리)|(?<selfHarm>자해)",
    )

    /** 이름 없는 기존 형식 — 하위호환 확인용. */
    private val legacy = CrisisKeywordScanner(
        "(자살|자해|죽고\\s?싶|죽어\\s?버|뛰어내리)",
    )

    @Test
    fun `자해는 self_harm 으로 분류된다`() {
        val r = classified.scan("자해를 했어요")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedPattern).isEqualTo("self_harm")
    }

    @Test
    fun `자살 의도는 suicide_intent 로 분류된다`() {
        val r = classified.scan("자살하고 싶어요")

        assertThat(r.matched).isTrue()
        assertThat(r.matchedPattern).isEqualTo("suicide_intent")
    }

    @Test
    fun `자해와 자살은 최고 등급으로 유지된다`() {
        // 3단 등급 도입이 완화를 뜻하면 안 된다. 등급 이전에는 *어떤* 매칭이든 LLM 을 건너뛰고
        // 위기 화면을 강제했으므로, 그 동작을 유지한다는 것은 곧 critical 이라는 뜻이다.
        assertThat(classified.scan("자해를 했어요").severity).isEqualTo("critical")
        assertThat(classified.scan("자살하고 싶어요").severity).isEqualTo("critical")
    }

    @Test
    fun `이름 없는 기존 regex 도 계속 매칭된다`() {
        val r = legacy.scan("자해를 했어요")

        assertThat(r.matched).isTrue()
        assertThat(r.severity).isEqualTo("critical")
    }

    @Test
    fun `분류 불가일 때는 가장 보수적인 값으로 떨어진다`() {
        // 운영자가 환경변수로 임의 regex 를 넣어도 안전 쪽으로 기운다.
        // 여기서 'high' 로 떨어뜨리면 override 를 쓰는 운영자만 조용히 LLM 호출·분류 진행으로
        // 바뀐다 — 등급 도입이 곧 보호 약화가 되는 경로라 fallback 은 critical 이어야 한다.
        val r = legacy.scan("뛰어내리고 싶어")

        assertThat(r.severity).isEqualTo("critical")
        assertThat(r.matchedPattern).isEqualTo("crisis_unclassified")
    }

    @Test
    fun `매칭 안 되면 분류도 없다`() {
        val r = classified.scan("오늘 조금 지쳤어요")

        assertThat(r.matched).isFalse()
        assertThat(r.matchedPattern).isNull()
        assertThat(r.severity).isNull()
    }

    // ───────────────────── 등급 우선순위 (3단 등급 도입과 함께 생긴 구멍) ─────────────────────

    /** 등급이 섞인 최소 fixture — medium 토큰이 critical 토큰보다 *앞* 에 오도록 문장을 만든다. */
    private val tiered = CrisisKeywordScanner(
        "(?<suicideIntent>자살)|(?<despairIdeation>사라지고 싶)|(?<riskSignal>마지막)",
    )

    @Test
    fun `한 문장에 등급이 섞이면 가장 심각한 매칭이 판정이다`() {
        // 첫 매칭에서 멈추면 '마지막'(medium)이 이겨서 자살 언급이 조용한 카드로 끝난다.
        // 등급이 전부 high 이던 시절에는 드러나지 않던 구멍이다.
        val r = tiered.scan("마지막으로 정리하고 있어요. 자살할 생각입니다.")

        assertThat(r.severity).isEqualTo("critical")
        assertThat(r.matchedPattern).isEqualTo("suicide_intent")
    }

    @Test
    fun `medium 이 뒤에 있어도 앞의 critical 을 끌어내리지 못한다`() {
        val r = tiered.scan("자살을 생각했어요. 마지막으로 쓰는 글입니다.")

        assertThat(r.severity).isEqualTo("critical")
    }

    @Test
    fun `등급 우선순위는 정렬이지 무조건 승격이 아니다`() {
        // 최고 등급을 고르는 로직이 '아무거나 걸리면 critical' 로 퇴화하지 않았는지 본다.
        // 이 줄이 빠지면 mostSevereMatch 를 상수 반환으로 바꿔도 위 두 테스트가 통과한다.
        assertThat(tiered.scan("마지막으로 정리하고 있어요.").severity).isEqualTo("medium")
        assertThat(tiered.scan("사라지고 싶어요.").severity).isEqualTo("high")
    }

    @Test
    fun `excerpt 해시는 판정에 쓰인 매칭 자리에서 뜬다`() {
        // 판정은 뒤쪽 critical 인데 해시는 앞쪽 medium 문맥에서 뜨면, 사후 추적이
        // 엉뚱한 구간을 가리킨다. 두 문장의 해시가 갈리는지로 확인한다.
        val front = tiered.scan("마지막으로 정리하고 있어요. 자살할 생각입니다.")
        val onlyMedium = tiered.scan("마지막으로 정리하고 있어요.")

        assertThat(front.excerptHash).isNotEqualTo(onlyMedium.excerptHash)
    }
}
