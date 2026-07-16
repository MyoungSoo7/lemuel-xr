package github.lms.lemuel.xr.content.domain

import java.time.OffsetDateTime

/** AR 1~7 주제 큐레이션 카드 도메인 모델 — 불변. */
data class TopicContent(
    val id: Long?,
    val topicId: Short?,
    val title: String?,
    val scriptureRef: String?,
    val body: String?,
    val anchorCharacter: String?,
    val targetEmotion: String?,
    val difficulty: Short?,
    val publishedAt: OffsetDateTime?,
)
