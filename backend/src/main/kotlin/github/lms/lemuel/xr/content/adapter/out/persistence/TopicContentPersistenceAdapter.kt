package github.lms.lemuel.xr.content.adapter.out.persistence

import github.lms.lemuel.xr.content.application.port.out.TopicContentPort
import github.lms.lemuel.xr.content.domain.TopicContent
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class TopicContentPersistenceAdapter(
    private val repository: TopicContentJpaRepository,
) : TopicContentPort {

    override fun findRelevant(topicId: Short, emotion: String?, limit: Int): List<TopicContent> =
        repository.findRelevant(topicId, emotion, PageRequest.of(0, limit)).map(::toDomain)

    private fun toDomain(e: TopicContentJpaEntity): TopicContent =
        TopicContent(
            e.id, e.topicId, e.title, e.scriptureRef,
            e.body, e.anchorCharacter, e.targetEmotion,
            e.difficulty, e.publishedAt,
        )
}
