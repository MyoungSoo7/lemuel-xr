package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.PracticeReflection
import org.springframework.data.domain.Pageable
import java.util.UUID

/** 실천/성찰(Theme 6·7) 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface PracticeReflectionPort {

    fun save(reflection: PracticeReflection): PracticeReflection

    fun findByUserIdAndTopicIdOrderByCreatedAtDesc(
        userId: UUID,
        topicId: Short,
        pageable: Pageable,
    ): List<PracticeReflection>

    fun countByUserIdAndTopicIdAndActionTakenTrue(userId: UUID, topicId: Short): Long
}
