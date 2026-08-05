package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.GameSession
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class ExitSessionUseCaseTest {

    private val sessions: GameSessionPort = mock()
    private val owner: UUID = UUID.randomUUID()
    private val uc = ExitSessionUseCase(sessions)

    private fun live(id: UUID): GameSession =
        GameSession.reconstitute(
            id, owner, null, null, null,
            LocalDateTime.now(), null, null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun completed(id: UUID): GameSession =
        GameSession.reconstitute(
            id, owner, null, null, null,
            LocalDateTime.now(), LocalDateTime.now(), null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun abandoned(id: UUID): GameSession =
        GameSession.reconstitute(
            id, owner, null, null, null,
            LocalDateTime.now(), null, LocalDateTime.now(), null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    @Test
    fun `execute reason atScene 지정`() {
        val sid = UUID.randomUUID()
        val e = live(sid)
        whenever(sessions.findById(sid)).thenReturn(Optional.of(e))

        val r = uc.execute(owner, sid, "panic", 3)

        assertThat(r.exitedAt).isNotNull()
        assertThat(e.abandonedAt).isNotNull()
        assertThat(e.finalOutcome).isEqualTo("safe_exit:panic")
        assertThat(e.sceneCountCompleted).isEqualTo(3.toShort())
    }

    @Test
    fun `execute reason null 이면 user_choice default`() {
        val sid = UUID.randomUUID()
        val e = live(sid)
        whenever(sessions.findById(sid)).thenReturn(Optional.of(e))

        uc.execute(owner, sid, null, null)

        assertThat(e.finalOutcome).isEqualTo("safe_exit:user_choice")
    }

    @Test
    fun `execute 세션없음 E_SESSION_NOT_FOUND`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.empty())
        assertThatThrownBy { uc.execute(owner, sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }

    @Test
    fun `execute 이미완료 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(completed(sid)))
        assertThatThrownBy { uc.execute(owner, sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute 이미abandoned E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(abandoned(sid)))
        assertThatThrownBy { uc.execute(owner, sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    // --- IDOR 회귀 ---

    @Test
    fun `남의 세션은 종료할 수 없다 - 존재를 숨기려 404`() {
        val sid = UUID.randomUUID()
        val e = live(sid)
        whenever(sessions.findById(sid)).thenReturn(Optional.of(e))

        assertThatThrownBy { uc.execute(UUID.randomUUID(), sid, "panic", 3) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)

        // 거부만으로는 부족하다 — 상태가 변하지 않았음을 확인한다.
        assertThat(e.abandonedAt).isNull()
        assertThat(e.finalOutcome).isNull()
    }

    @Test
    fun `userId 없는 레거시 세션은 아무도 종료할 수 없다`() {
        val sid = UUID.randomUUID()
        val legacy = GameSession.reconstitute(
            sid, null, null, null, null,
            LocalDateTime.now(), null, null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(legacy))

        assertThatThrownBy { uc.execute(owner, sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }
}
