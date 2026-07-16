package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 사용자 emergency exit — final_outcome='safe_exit' + abandoned_at.
 * SafetyController 의 /exit endpoint 에서 호출.
 */
@Service
class ExitSessionUseCase(
    private val sessions: GameSessionPort,
) {

    @Transactional
    fun execute(sessionId: UUID, reason: String?, atScene: Int?): Result {
        val session = sessions.findById(sessionId)
            .orElseThrow { AppException(ErrorCode.E_SESSION_NOT_FOUND) }
        if (session.isTerminated()) {
            throw AppException(ErrorCode.E_SESSION_INVALID)
        }
        session.exit(reason, atScene)
        sessions.save(session)
        return Result(session.id!!, session.abandonedAt)
    }

    data class Result(val sessionId: UUID, val exitedAt: LocalDateTime?)
}
