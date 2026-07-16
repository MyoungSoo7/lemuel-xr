package github.lms.lemuel.xr.values.adapter.out.persistence

import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import github.lms.lemuel.xr.values.domain.UserValuePractice
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID

/**
 * UserValuePracticePort 구현 — Spring Data JPA 리포지토리에 위임.
 *
 * 엔티티 ↔ 도메인 매핑의 유일한 위치. [UserValuePracticeJpaEntity] 는 이 클래스 밖으로 새지 않는다.
 */
@Component
class UserValuePracticePersistenceAdapter(
    private val repository: UserValuePracticeJpaRepository,
) : UserValuePracticePort {

    override fun findRecent(userId: UUID, since: OffsetDateTime): List<UserValuePractice> =
        repository.findRecent(userId, since).map(::toDomain)

    override fun save(practice: UserValuePractice): UserValuePractice =
        toDomain(repository.save(toEntity(practice)))

    // --- 매핑 ---

    private fun toDomain(e: UserValuePracticeJpaEntity): UserValuePractice =
        UserValuePractice(
            id = e.id,
            userId = e.userId!!,
            valueId = e.valueId!!,
            practicedAt = e.practicedAt!!,
            durationSec = e.durationSec,
            note = e.note,
            linkedCharacter = e.linkedCharacter,
            linkedGameSession = e.linkedGameSession,
        )

    private fun toEntity(d: UserValuePractice): UserValuePracticeJpaEntity =
        UserValuePracticeJpaEntity().apply {
            id = d.id
            userId = d.userId
            valueId = d.valueId
            practicedAt = d.practicedAt
            durationSec = d.durationSec
            note = d.note
            linkedCharacter = d.linkedCharacter
            linkedGameSession = d.linkedGameSession
        }
}
