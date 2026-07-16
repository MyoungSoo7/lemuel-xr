package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.UserPsalmPort
import github.lms.lemuel.xr.content.domain.UserPsalm
import org.springframework.stereotype.Component

@Component
class UserPsalmPersistenceAdapter(
    private val repository: UserPsalmJpaRepository,
) : UserPsalmPort {

    override fun save(psalm: UserPsalm): UserPsalm =
        toDomain(repository.save(toEntity(psalm)))

    private fun toDomain(p: UserPsalmJpaEntity): UserPsalm =
        UserPsalm(
            p.id, p.userId, p.psalmForm, p.rawText,
            p.polishedText, p.acceptedPolished, p.inspiredByPsalm,
            p.createdAt,
        )

    private fun toEntity(d: UserPsalm): UserPsalmJpaEntity =
        UserPsalmJpaEntity().apply {
            id = d.id
            userId = d.userId
            psalmForm = d.psalmForm
            rawText = d.rawText
            polishedText = d.polishedText
            if (d.acceptedPolished != null) {
                acceptedPolished = d.acceptedPolished
            }
            inspiredByPsalm = d.inspiredByPsalm
            createdAt = d.createdAt
        }
}
