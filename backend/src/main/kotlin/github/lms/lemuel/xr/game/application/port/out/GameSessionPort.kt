package github.lms.lemuel.xr.game.application.port.out

import github.lms.lemuel.xr.game.domain.GameSession
import java.util.Optional
import java.util.UUID

/**
 * 게임 세션 영속화 아웃바운드 포트.
 *
 * ISP — 게임 컨텍스트 유스케이스(start/decide/complete/exit)가 실제로 쓰는
 * `findById` / `save` 만 노출. CRUD superset 은 제외.
 *
 * 포트는 순수 도메인 [GameSession] 만 주고받는다. Hibernate 엔티티는
 * 어댑터 경계 안에 머문다.
 */
interface GameSessionPort {
    fun findById(id: UUID): Optional<GameSession>

    fun save(session: GameSession): GameSession
}
