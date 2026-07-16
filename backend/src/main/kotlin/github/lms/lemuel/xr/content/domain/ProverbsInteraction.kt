package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime
import java.util.UUID

/** 기준2 잠언 상호작용 도메인 모델 — 불변. recommendedProverbs 는 JSONB 페이로드. */
data class ProverbsInteraction(
    val id: Long?,
    val userId: UUID?,
    val userSituation: String?,
    val recommendedProverbs: List<Map<String, Any?>>?,
    val chosenProverbRef: String?,
    val chosenDimension: String?,
    val createdAt: LocalDateTime?,
)
