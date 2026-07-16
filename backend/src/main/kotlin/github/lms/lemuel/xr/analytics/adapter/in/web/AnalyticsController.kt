package github.lms.lemuel.xr.analytics.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * /api/analytics/event — XR 클라이언트가 미세 이벤트 ship.
 *
 * Phase 1 단계: log + outbox 만. Phase 2 에서 elastic 으로 직접 ship.
 */
@RestController
@RequestMapping("/api/analytics")
class AnalyticsController {

    @PostMapping("/event")
    fun event(@RequestBody batch: EventBatch): ResponseEntity<AcceptResponse> {
        val userId = RequestContext.currentUserId()
        val count = batch.events?.size ?: 0
        log.info(
            "Analytics events accepted user={} session={} count={}",
            userId, batch.sessionId, count,
        )
        // TODO Phase 2: interaction_events 테이블 INSERT + ELK ship 비동기
        return ResponseEntity.ok(AcceptResponse(count))
    }

    data class EventBatch(val sessionId: UUID?, val events: List<Map<String, Any>>?)

    data class AcceptResponse(val accepted: Int)

    companion object {
        private val log = LoggerFactory.getLogger(AnalyticsController::class.java)
    }
}
