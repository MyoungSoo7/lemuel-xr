package github.lms.lemuel.xr.auth.application

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.User
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetCurrentUserUseCase(
    private val users: UserPort,
) {

    @Transactional(readOnly = true)
    fun execute(userId: UUID): User =
        users.findById(userId)
            .orElseThrow { AppException(ErrorCode.E_AUTH_REQUIRED, "User not found") }
}
