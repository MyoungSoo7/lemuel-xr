package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.PracticeReflectionPort
import github.lms.lemuel.xr.content.domain.PracticeReflection
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PracticeReflectionPersistenceAdapter(
    private val repository: PracticeReflectionJpaRepository,
) : PracticeReflectionPort {

    override fun save(reflection: PracticeReflection): PracticeReflection =
        toDomain(repository.save(toEntity(reflection)))

    override fun findByUserIdAndTopicIdOrderByCreatedAtDesc(
        userId: UUID,
        topicId: Short,
        pageable: Pageable,
    ): List<PracticeReflection> =
        repository.findByUserIdAndTopicIdOrderByCreatedAtDesc(userId, topicId, pageable).map(::toDomain)

    override fun countByUserIdAndTopicIdAndActionTakenTrue(userId: UUID, topicId: Short): Long =
        repository.countByUserIdAndTopicIdAndActionTakenTrue(userId, topicId)

    private fun toDomain(e: PracticeReflectionJpaEntity): PracticeReflection =
        PracticeReflection(
            e.id, e.userId, e.topicId, e.practiceKind,
            e.situation, e.reflection, e.actionTaken,
            e.scriptureRef, e.dimension, e.createdAt,
        )

    private fun toEntity(d: PracticeReflection): PracticeReflectionJpaEntity =
        PracticeReflectionJpaEntity().apply {
            id = d.id
            userId = d.userId
            topicId = d.topicId
            practiceKind = d.practiceKind
            situation = d.situation
            reflection = d.reflection
            if (d.actionTaken != null) {
                actionTaken = d.actionTaken
            }
            scriptureRef = d.scriptureRef
            dimension = d.dimension
            createdAt = d.createdAt
        }
}
