package github.lms.lemuel.xr.content.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EcclesiastesViewJpaRepository : JpaRepository<EcclesiastesViewJpaEntity, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<EcclesiastesViewJpaEntity>

    /** §4.4 신호 — 결론(전 12:13)까지 함께 본 view 누적 수. nihilism 방지 지표. */
    fun countByUserIdAndConclusionViewedTrue(userId: UUID): Long
}
