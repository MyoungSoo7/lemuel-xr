package github.lms.lemuel.xr.game.adapter.out.persistence

import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.GameSession
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.UUID

/**
 * [GameSessionPort] 어댑터 — 순수 도메인 [GameSession] 과 Hibernate
 * [GameSessionJpaEntity] 사이를 매핑한다. JPA 엔티티는 이 경계 안에만 존재.
 *
 * 매핑은 리플렉션 없이 필드 대응으로 수동 처리. 엔티티에만 있고 도메인에 없는
 * `assetsManifestVersion`·`appSessionId` 등도 도메인에 포함시켜 왕복(round-trip)
 * 손실이 없게 한다.
 */
@Component
class GameSessionPersistenceAdapter(
    private val repository: GameSessionJpaRepository,
) : GameSessionPort {

    override fun findById(id: UUID): Optional<GameSession> =
        repository.findById(id).map(GameSessionMapper::toDomain)

    override fun save(session: GameSession): GameSession {
        val saved = repository.save(GameSessionMapper.toEntity(session))
        return GameSessionMapper.toDomain(saved)
    }
}
