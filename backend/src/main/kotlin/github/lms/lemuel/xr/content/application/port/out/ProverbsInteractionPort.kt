package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.ProverbsInteraction

/** 잠언 상호작용(기준2) 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface ProverbsInteractionPort {

    fun save(interaction: ProverbsInteraction): ProverbsInteraction
}
