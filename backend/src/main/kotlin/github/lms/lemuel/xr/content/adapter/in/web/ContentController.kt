package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.content.application.GetTopicCardsUseCase
import github.lms.lemuel.xr.content.domain.Topic
import github.lms.lemuel.xr.content.domain.TopicContent
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/** /api/content/topics — 7 주제 카탈로그 + 큐레이션 카드. 인증 불필요. */
@RestController
@RequestMapping("/api/content")
class ContentController(
    private val getTopicCards: GetTopicCardsUseCase,
) {

    @GetMapping("/topics")
    fun topics(): ResponseEntity<TopicsResponse> {
        val items = Topic.entries.map { TopicDto(it.id, it.name, it.title) }
        return ResponseEntity.ok(TopicsResponse(items))
    }

    @GetMapping("/topics/{topicId}/scene")
    fun scene(
        @PathVariable topicId: Int,
        @RequestParam(defaultValue = "AUTO") mode: String,
    ): ResponseEntity<TopicSceneDto> {
        val t = Topic.byId(topicId)
        return ResponseEntity.ok(
            TopicSceneDto(
                topicId, t.title, mode,
                SceneAssetsDto(
                    "skybox-" + t.name.lowercase() + ".exr",
                    "bgm-soft",
                    "narration-" + t.name.lowercase() + "-" + mode.lowercase(),
                ),
                240,
            ),
        )
    }

    /**
     * /api/content/topics/{topicId}/cards — 큐레이션 카드 목록.
     * AR 1~7 일상 영적 양식의 핵심 콘텐츠.
     */
    @GetMapping("/topics/{topicId}/cards")
    fun cards(
        @PathVariable topicId: Int,
        @RequestParam(required = false) emotion: String?,
        @RequestParam(defaultValue = "5") limit: Int,
    ): ResponseEntity<CardsResponse> {
        Topic.byId(topicId) // 검증
        val items = getTopicCards.cards(topicId, emotion, limit).map { toCard(it) }
        return ResponseEntity.ok(CardsResponse(topicId, items))
    }

    private fun toCard(c: TopicContent): CardDto =
        CardDto(
            c.id, c.topicId, c.title,
            c.scriptureRef, c.body,
            c.anchorCharacter, c.targetEmotion, c.difficulty,
            c.publishedAt,
        )

    // --- DTOs ---

    data class TopicsResponse(val topics: List<TopicDto>)
    data class TopicDto(val id: Int, val key: String, val title: String)
    data class TopicSceneDto(
        val topicId: Int,
        val title: String,
        val mode: String,
        val scene: SceneAssetsDto,
        val estimatedDurationSec: Int,
    )
    data class SceneAssetsDto(val skybox: String, val bgmId: String, val narrationId: String)

    data class CardsResponse(val topicId: Int, val cards: List<CardDto>)
    data class CardDto(
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
}
