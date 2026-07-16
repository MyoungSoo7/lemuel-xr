package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.EcclesiastesViewPort
import github.lms.lemuel.xr.content.domain.EcclesiastesView
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EcclesiastesViewPersistenceAdapter(
    private val repository: EcclesiastesViewJpaRepository,
) : EcclesiastesViewPort {

    override fun save(view: EcclesiastesView): EcclesiastesView =
        toDomain(repository.save(toEntity(view)))

    override fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<EcclesiastesView> =
        repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(::toDomain)

    override fun countByUserIdAndConclusionViewedTrue(userId: UUID): Long =
        repository.countByUserIdAndConclusionViewedTrue(userId)

    private fun toDomain(e: EcclesiastesViewJpaEntity): EcclesiastesView =
        EcclesiastesView(
            e.id, e.userId, e.chapterRef, e.userSeason,
            e.futilityNote, e.meaningNote, e.listenedAudio,
            e.conclusionViewed, e.createdAt,
        )

    private fun toEntity(d: EcclesiastesView): EcclesiastesViewJpaEntity =
        EcclesiastesViewJpaEntity().apply {
            id = d.id
            userId = d.userId
            chapterRef = d.chapterRef
            userSeason = d.userSeason
            futilityNote = d.futilityNote
            meaningNote = d.meaningNote
            if (d.listenedAudio != null) {
                listenedAudio = d.listenedAudio
            }
            if (d.conclusionViewed != null) {
                conclusionViewed = d.conclusionViewed
            }
            createdAt = d.createdAt
        }
}
