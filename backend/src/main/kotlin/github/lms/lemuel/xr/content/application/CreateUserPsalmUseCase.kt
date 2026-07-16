package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.application.port.out.UserPsalmPort
import github.lms.lemuel.xr.content.domain.UserPsalm
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/** Theme 4 사용자 시편 작성 유스케이스. 도메인 모델 구성 로직을 컨트롤러에서 이관. */
@Service
class CreateUserPsalmUseCase(
    private val userPsalms: UserPsalmPort,
) {

    fun create(userId: UUID, text: String?, form: String?, inspiredBy: String?): UserPsalm {
        val psalm = UserPsalm(
            id = UUID.randomUUID(),
            userId = userId,
            psalmForm = form,
            rawText = text,
            polishedText = null,
            acceptedPolished = null,
            inspiredByPsalm = inspiredBy,
            createdAt = LocalDateTime.now(),
        )
        return userPsalms.save(psalm)
    }
}
