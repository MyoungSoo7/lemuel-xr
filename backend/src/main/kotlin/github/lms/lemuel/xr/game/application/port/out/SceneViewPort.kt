package github.lms.lemuel.xr.game.application.port.out

import github.lms.lemuel.xr.game.domain.SceneView
import java.util.UUID

/**
 * Scene 조회 이력 아웃바운드 포트.
 *
 * ISP — 세션별 Scene 진입 이력 조회만 노출. CRUD superset 은 제외.
 *
 * 포트는 순수 도메인 [SceneView] 만 반환한다.
 */
interface SceneViewPort {
    fun findByGameSessionIdOrderByEnteredAt(gameSessionId: UUID): List<SceneView>
}
