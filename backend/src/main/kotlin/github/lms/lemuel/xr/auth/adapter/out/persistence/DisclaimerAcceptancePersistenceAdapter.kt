package github.lms.lemuel.xr.auth.adapter.out.persistence

import github.lms.lemuel.xr.auth.application.port.out.DisclaimerAcceptancePort
import github.lms.lemuel.xr.auth.domain.DisclaimerAcceptance
import org.springframework.stereotype.Component

/**
 * [DisclaimerAcceptancePort] 구현 — Spring Data [DisclaimerAcceptanceJpaRepository] 위임.
 *
 * 이 클래스가 [DisclaimerAcceptanceJpaEntity] 를 import 하는 유일한 application/adapter 지점.
 */
@Component
class DisclaimerAcceptancePersistenceAdapter(
    private val repository: DisclaimerAcceptanceJpaRepository,
) : DisclaimerAcceptancePort {

    override fun save(acceptance: DisclaimerAcceptance): DisclaimerAcceptance =
        toDomain(repository.save(toEntity(acceptance)))

    private fun toDomain(e: DisclaimerAcceptanceJpaEntity): DisclaimerAcceptance =
        DisclaimerAcceptance(
            e.id,
            e.userId,
            e.acceptedAt,
            e.disclaimerVersion,
            e.userAgent,
            e.ipHash,
        )

    private fun toEntity(a: DisclaimerAcceptance): DisclaimerAcceptanceJpaEntity =
        DisclaimerAcceptanceJpaEntity().apply {
            // id 는 IDENTITY 생성 — 신규는 null 로 두어 INSERT 유도.
            a.id?.let { id = it }
            userId = a.userId
            acceptedAt = a.acceptedAt
            disclaimerVersion = a.disclaimerVersion
            userAgent = a.userAgent
            ipHash = a.ipHash
        }
}
