package github.lms.lemuel.xr.analytics.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/analytics/event — XR 클라이언트가 미세 이벤트 ship.
 *
 * <p>Phase 1 단계: log + outbox 만. Phase 2 에서 elastic 으로 직접 ship.</p>
 */
@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    @PostMapping("/event")
    public ResponseEntity<AcceptResponse> event(@RequestBody EventBatch batch) {
        UUID userId = RequestContext.currentUserId();
        log.info("Analytics events accepted user={} session={} count={}",
                userId, batch.sessionId(), batch.events() == null ? 0 : batch.events().size());
        // TODO Phase 2: interaction_events 테이블 INSERT + ELK ship 비동기
        return ResponseEntity.ok(new AcceptResponse(
                batch.events() == null ? 0 : batch.events().size()));
    }

    public record EventBatch(UUID sessionId, List<Map<String, Object>> events) {}
    public record AcceptResponse(int accepted) {}
}
