package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.ProverbsInteractionPort
import github.lms.lemuel.xr.content.domain.ProverbsInteraction
import org.springframework.stereotype.Component

@Component
class ProverbsInteractionPersistenceAdapter(
    private val repository: ProverbsInteractionJpaRepository,
) : ProverbsInteractionPort {

    override fun save(interaction: ProverbsInteraction): ProverbsInteraction =
        toDomain(repository.save(toEntity(interaction)))

    private fun toDomain(e: ProverbsInteractionJpaEntity): ProverbsInteraction =
        ProverbsInteraction(
            e.id, e.userId, e.userSituation, e.recommendedProverbs,
            e.chosenProverbRef, e.chosenDimension, e.createdAt,
        )

    private fun toEntity(d: ProverbsInteraction): ProverbsInteractionJpaEntity =
        ProverbsInteractionJpaEntity().apply {
            id = d.id
            userId = d.userId
            userSituation = d.userSituation
            recommendedProverbs = d.recommendedProverbs
            chosenProverbRef = d.chosenProverbRef
            chosenDimension = d.chosenDimension
            createdAt = d.createdAt
        }
}
