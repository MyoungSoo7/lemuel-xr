package github.lms.lemuel.xr.values.adapter.out.persistence

import github.lms.lemuel.xr.values.application.port.out.UserValueProfilePort
import github.lms.lemuel.xr.values.domain.UserValueProfile
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.UUID

/**
 * UserValueProfilePort 구현 — Spring Data JPA 리포지토리에 위임.
 *
 * 엔티티 ↔ 도메인 매핑의 유일한 위치. [UserValueProfileJpaEntity] 는 이 클래스 밖으로 새지 않는다.
 */
@Component
class UserValueProfilePersistenceAdapter(
    private val repository: UserValueProfileJpaRepository,
) : UserValueProfilePort {

    override fun findByUserId(userId: UUID): Optional<UserValueProfile> =
        repository.findByUserId(userId).map(::toDomain)

    override fun save(profile: UserValueProfile): UserValueProfile =
        toDomain(repository.save(toEntity(profile)))

    // --- 매핑 ---

    private fun toDomain(e: UserValueProfileJpaEntity): UserValueProfile =
        UserValueProfile.of(
            id = e.id!!,
            userId = e.userId!!,
            valuesJson = e.valuesJson,
            startedAt = e.startedAt!!,
            lastUpdatedAt = e.lastUpdatedAt!!,
        )

    private fun toEntity(d: UserValueProfile): UserValueProfileJpaEntity =
        UserValueProfileJpaEntity().apply {
            id = d.id
            userId = d.userId
            valuesJson = HashMap(d.valuesJson)
            startedAt = d.startedAt
            lastUpdatedAt = d.lastUpdatedAt
        }
}
