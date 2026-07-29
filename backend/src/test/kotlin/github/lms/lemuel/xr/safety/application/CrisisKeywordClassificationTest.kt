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
 * 분류를 정확하게 만드는 것이 목적이지 완화가 목적이 아니다. 현재 등록된 키워드는
 * 모두 고위험이므로 severity 는 전부 high 를 유지한다. 다만 앞으로 저위험 키워드를
 * 추가할 때 올바른 tier 를 줄 수 있도록 *데이터로* 분류하게 바꾼다.
 *
 * 하위호환: 이름 없는 기존 regex(SAFETY_CRISIS_REGEX 환경변수 override 포함)도
 * 계속 동작해야 한다. 그 경우 가장 보수적인 분류로 떨어진다.
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
    fun `자해도 고위험으로 유지된다`() {
        // 분류가 정교해졌다고 해서 보호가 약해지면 안 된다.
        // alert-severity-threshold=medium 이므로 high 여야 위기 자원이 노출된다.
        assertThat(classified.scan("자해를 했어요").severity).isEqualTo("high")
        assertThat(classified.scan("자살하고 싶어요").severity).isEqualTo("high")
    }

    @Test
    fun `이름 없는 기존 regex 도 계속 매칭된다`() {
        val r = legacy.scan("자해를 했어요")

        assertThat(r.matched).isTrue()
        assertThat(r.severity).isEqualTo("high")
    }

    @Test
    fun `분류 불가일 때는 가장 보수적인 값으로 떨어진다`() {
        // 운영자가 환경변수로 임의 regex 를 넣어도 안전 쪽으로 기운다.
        val r = legacy.scan("뛰어내리고 싶어")

        assertThat(r.severity).isEqualTo("high")
        assertThat(r.matchedPattern).isEqualTo("crisis_unclassified")
    }

    @Test
    fun `매칭 안 되면 분류도 없다`() {
        val r = classified.scan("오늘 조금 지쳤어요")

        assertThat(r.matched).isFalse()
        assertThat(r.matchedPattern).isNull()
        assertThat(r.severity).isNull()
    }
}
