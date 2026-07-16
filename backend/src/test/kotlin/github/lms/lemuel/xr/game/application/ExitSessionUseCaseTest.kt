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
    private val uc = ExitSessionUseCase(sessions)

    private fun live(id: UUID): GameSession =
        GameSession.reconstitute(
            id, null, null, null, null,
            LocalDateTime.now(), null, null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun completed(id: UUID): GameSession =
        GameSession.reconstitute(
            id, null, null, null, null,
            LocalDateTime.now(), LocalDateTime.now(), null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    private fun abandoned(id: UUID): GameSession =
        GameSession.reconstitute(
            id, null, null, null, null,
            LocalDateTime.now(), null, LocalDateTime.now(), null, null, null, null,
            0.toShort(), null, null, null, null,
        )

    @Test
    fun `execute reason atScene 지정`() {
        val sid = UUID.randomUUID()
        val e = live(sid)
        whenever(sessions.findById(sid)).thenReturn(Optional.of(e))

        val r = uc.execute(sid, "panic", 3)

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

        uc.execute(sid, null, null)

        assertThat(e.finalOutcome).isEqualTo("safe_exit:user_choice")
    }

    @Test
    fun `execute 세션없음 E_SESSION_NOT_FOUND`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.empty())
        assertThatThrownBy { uc.execute(sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }

    @Test
    fun `execute 이미완료 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(completed(sid)))
        assertThatThrownBy { uc.execute(sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute 이미abandoned E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(abandoned(sid)))
        assertThatThrownBy { uc.execute(sid, "x", null) }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }
}
