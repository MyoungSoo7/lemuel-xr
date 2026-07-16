package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.TopicContent

/** 주제 큐레이션 카드 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface TopicContentPort {

    /** 관련 카드 상위 [limit] 개 (난이도·id 순). Spring Data 타입을 포트에 노출하지 않는다. */
    fun findRelevant(topicId: Short, emotion: String?, limit: Int): List<TopicContent>
}
