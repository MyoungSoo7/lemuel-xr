package github.lms.lemuel.xr.auth.adapter.out.persistence

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.auth.domain.User
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.UUID

/**
 * [UserPort] 구현 — Spring Data [UserJpaRepository] 위임.
 *
 * 이 클래스가 [UserJpaEntity] 를 import 하는 유일한 application/adapter 지점.
 * [toDomain]/[toEntity] 로 도메인 ↔ 엔티티 매핑을 격리한다.
 */
@Component
class UserPersistenceAdapter(
    private val repository: UserJpaRepository,
) : UserPort {

    override fun findById(id: UUID): Optional<User> =
        repository.findById(id).map(::toDomain)

    override fun save(user: User): User =
        toDomain(repository.save(toEntity(user)))

    private fun toDomain(e: UserJpaEntity): User =
        User(
            e.id,
            e.createdAt,
            e.updatedAt,
            e.userType,
            e.externalId,
            e.faithTone,
            e.preferredMode,
            e.hapticIntensity,
            e.skipIntroSilence,
            e.dataRetentionDays,
            e.deletedAt,
            e.disclaimerAcceptedAt,
            e.disclaimerVersion,
            e.aiOptOut,
        )

    private fun toEntity(u: User): UserJpaEntity =
        UserJpaEntity().apply {
            id = u.id
            createdAt = u.createdAt
            updatedAt = u.updatedAt
            userType = u.userType
            externalId = u.externalId
            faithTone = u.faithTone
            preferredMode = u.preferredMode
            hapticIntensity = u.hapticIntensity
            skipIntroSilence = u.skipIntroSilence
            dataRetentionDays = u.dataRetentionDays
            deletedAt = u.deletedAt
            disclaimerAcceptedAt = u.disclaimerAcceptedAt
            disclaimerVersion = u.disclaimerVersion
            // ai_opt_out NOT NULL — 도메인이 null 이면 엔티티 기본값(FALSE) 유지.
            u.aiOptOut?.let { aiOptOut = it }
        }
}
