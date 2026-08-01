package github.lms.lemuel.xr.game.adapter.out.optout

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

/**
 * AI opt-out 조회의 fail-closed 계약 검증.
 *
 * 이 어댑터가 예외를 흘리거나 실패 시 `false` 를 주면, AI 를 명시적으로 끈 사용자에게 LLM 호출이 나간다.
 * 되돌릴 수 없는 방향의 실패이므로 "정상 경로" 보다 "실패 경로" 를 더 많이 못 박는다.
 */
class AuthAiOptOutAdapterTest {

    private val users: UserPort = mock()
    private val adapter = AuthAiOptOutAdapter(users)

    private fun user(id: UUID, optOut: Boolean?) = User(
        id, null, null, "guest", null, "balanced", null, "medium",
        false, 90, null, null, null, optOut,
    )

    @Test
    fun `ai_opt_out true 면 opt-out`() {
        val id = UUID.randomUUID()
        whenever(users.findById(id)).thenReturn(Optional.of(user(id, true)))

        assertThat(adapter.isOptedOut(id)).isTrue()
    }

    @Test
    fun `ai_opt_out false 면 opt-out 아님`() {
        val id = UUID.randomUUID()
        whenever(users.findById(id)).thenReturn(Optional.of(user(id, false)))

        assertThat(adapter.isOptedOut(id)).isFalse()
    }

    @Test
    fun `userId 가 null 이면 opt-out 으로 간주한다`() {
        // R5 는 LLM 을 *명시적 opt-in* 으로 규정한다. 조회할 레코드가 없다는 건
        // "동의 기록이 없다" 는 뜻이지 "동의했다" 는 뜻이 아니다.
        assertThat(adapter.isOptedOut(null)).isTrue()
    }

    @Test
    fun `사용자를 못 찾으면 opt-out 으로 간주한다`() {
        whenever(users.findById(any())).thenReturn(Optional.empty())

        assertThat(adapter.isOptedOut(UUID.randomUUID())).isTrue()
    }

    @Test
    fun `ai_opt_out 이 null 이면 opt-out 으로 간주한다`() {
        val id = UUID.randomUUID()
        whenever(users.findById(id)).thenReturn(Optional.of(user(id, null)))

        assertThat(adapter.isOptedOut(id)).isTrue()
    }

    @Test
    fun `조회가 터져도 예외를 흘리지 않고 opt-out 으로 간주한다`() {
        // DB 장애 = 사용자가 끈 스위치를 무시해도 되는 이유가 아니다.
        // 이 경우의 대가는 그 회차 응답이 정적 큐레이션 문장이 되는 것뿐이다 —
        // LLM 사이드카 장애 시 이미 지나가는 경로와 같다.
        whenever(users.findById(any())).thenThrow(RuntimeException("DB down"))

        assertThat(adapter.isOptedOut(UUID.randomUUID())).isTrue()
    }
}
