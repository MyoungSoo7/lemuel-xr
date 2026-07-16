package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.EcclesiastesView
import org.springframework.data.domain.Pageable
import java.util.UUID

/** 전도서 view(기준4) 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface EcclesiastesViewPort {

    fun save(view: EcclesiastesView): EcclesiastesView

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<EcclesiastesView>

    /** §4.4 신호 — 결론(전 12:13)까지 함께 본 view 누적 수. nihilism 방지 지표. */
    fun countByUserIdAndConclusionViewedTrue(userId: UUID): Long
}
