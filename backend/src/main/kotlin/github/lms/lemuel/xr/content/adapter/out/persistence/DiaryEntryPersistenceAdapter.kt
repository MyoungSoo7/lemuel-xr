package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.DiaryEntryPort
import github.lms.lemuel.xr.content.domain.DiaryEntry
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DiaryEntryPersistenceAdapter(
    private val repository: DiaryEntryJpaRepository,
) : DiaryEntryPort {

    override fun save(entry: DiaryEntry): DiaryEntry =
        toDomain(repository.save(toEntity(entry)))

    override fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<DiaryEntry> =
        repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(::toDomain)

    private fun toDomain(e: DiaryEntryJpaEntity): DiaryEntry =
        DiaryEntry(
            e.id, e.userId, e.body, e.formType,
            e.emotionLabel, e.intensity, e.wordCount,
            e.meditationText, e.meditationAccepted,
            e.createdAt, e.updatedAt,
        )

    private fun toEntity(d: DiaryEntry): DiaryEntryJpaEntity =
        DiaryEntryJpaEntity().apply {
            id = d.id
            userId = d.userId
            body = d.body
            formType = d.formType
            emotionLabel = d.emotionLabel
            intensity = d.intensity
            wordCount = d.wordCount
            meditationText = d.meditationText
            if (d.meditationAccepted != null) {
                meditationAccepted = d.meditationAccepted
            }
            createdAt = d.createdAt
            updatedAt = d.updatedAt
        }
}
