package github.lms.lemuel.xr.game.adapter.out.crisis

import github.lms.lemuel.xr.safety.application.GetCrisisResourcesUseCase
import github.lms.lemuel.xr.safety.domain.CrisisResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 위기 연락처 조회의 fail-safe 계약 검증.
 *
 * 이 어댑터가 예외를 흘리거나 빈 값을 주면 시나리오의 `{{crisis_resources.default}}` 가
 * 그대로 사용자 화면에 남는다. 그래서 "정상 경로" 보다 "실패 경로" 를 더 많이 못 박는다.
 */
class SafetyCrisisContactAdapterTest {

    private val uc: GetCrisisResourcesUseCase = mock()
    private val adapter = SafetyCrisisContactAdapter(uc, "109")

    private fun resource(name: String, type: String, value: String, priority: Short) =
        CrisisResource(
            null, "KR", "ko-KR", name, type, value,
            null, "24/7", "suicide", priority, true, null,
        )

    @Test
    fun `우선순위 1위 전화 자원의 번호를 준다`() {
        whenever(uc.execute("KR", "ko-KR")).thenReturn(
            listOf(
                resource("자살예방 상담전화", "phone", "109", 1),
                resource("정신건강위기상담전화", "phone", "1577-0199", 2),
            ),
        )

        assertThat(adapter.defaultContact("KR", "ko-KR")).isEqualTo("109")
    }

    @Test
    fun `전화가 아닌 자원은 건너뛴다`() {
        // 카탈로그 1순위가 웹 포털일 수 있다 — 사용자가 *걸* 수 있는 번호만 유효하다.
        whenever(uc.execute(any(), any())).thenReturn(
            listOf(
                resource("정신건강정보 포털", "web", "https://www.mentalhealth.go.kr", 1),
                resource("자살예방 상담전화", "phone", "109", 2),
            ),
        )

        assertThat(adapter.defaultContact("KR", "ko-KR")).isEqualTo("109")
    }

    @Test
    fun `자원이 없으면 설정 대체값으로 떨어진다`() {
        whenever(uc.execute(any(), any())).thenReturn(emptyList())

        assertThat(adapter.defaultContact("KR", "ko-KR")).isEqualTo("109")
    }

    @Test
    fun `번호가 공백이면 설정 대체값으로 떨어진다`() {
        whenever(uc.execute(any(), any())).thenReturn(listOf(resource("깨진 시드", "phone", "  ", 1)))

        assertThat(adapter.defaultContact("KR", "ko-KR")).isEqualTo("109")
    }

    @Test
    fun `조회가 터져도 예외를 흘리지 않고 대체값을 준다`() {
        // DB 장애 = 위기 문구가 안 나가도 되는 이유가 아니다.
        whenever(uc.execute(any(), any())).thenThrow(RuntimeException("DB down"))

        assertThat(adapter.defaultContact("KR", "ko-KR")).isEqualTo("109")
    }

    @Test
    fun `대체값은 설정에서 온다`() {
        val other = SafetyCrisisContactAdapter(uc, "988")
        whenever(uc.execute(any(), any())).thenReturn(emptyList())

        assertThat(other.defaultContact("US", "en-US")).isEqualTo("988")
    }
}
