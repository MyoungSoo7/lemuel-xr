package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.application.port.out.TopicContentPort
import github.lms.lemuel.xr.content.domain.TopicContent
import org.springframework.stereotype.Service
import kotlin.math.min

/** 주제 큐레이션 카드 조회 유스케이스 — AR 1~7 일상 영적 양식 핵심 콘텐츠. */
@Service
class GetTopicCardsUseCase(
    private val topicContents: TopicContentPort,
) {

    fun cards(topicId: Int, emotion: String?, limit: Int): List<TopicContent> =
        topicContents.findRelevant(topicId.toShort(), emotion, min(limit, 20))
}
