package github.lms.lemuel.xr.content.domain

import java.time.LocalDateTime
import java.util.UUID

/** Theme 6/7 실천·성찰 도메인 모델 — 불변. reflection 은 JSONB 페이로드. */
data class PracticeReflection(
    val id: Long?,
    val userId: UUID?,
    val topicId: Short?,
    val practiceKind: String?,
    val situation: String?,
    val reflection: Map<String, Any?>?,
    val actionTaken: Boolean?,
    val scriptureRef: String?,
    val dimension: String?,
    val createdAt: LocalDateTime?,
)
