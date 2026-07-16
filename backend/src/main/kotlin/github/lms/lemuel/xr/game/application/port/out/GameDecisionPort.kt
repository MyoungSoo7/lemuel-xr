package github.lms.lemuel.xr.game.application.port.out

import github.lms.lemuel.xr.game.domain.GameDecision

/**
 * 게임 결정 영속화 아웃바운드 포트.
 *
 * ISP — `DecideSceneUseCase` 가 실제로 쓰는 `save` 만 노출한다.
 * JpaRepository 의 CRUD superset 은 의도적으로 제외.
 *
 * 포트는 순수 도메인 [GameDecision] 만 주고받는다.
 */
interface GameDecisionPort {
    fun save(decision: GameDecision): GameDecision
}
