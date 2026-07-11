package github.lms.lemuel.xr.journey.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.journey.application.GetJourneyUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/journey/* — 사용자 일주일 여정 (CROSS-MAPPING-VR-AR.md §4).
 *
 * <ul>
 *   <li>GET /api/journey/weekly  — 이번 주 인물·가치·테마</li>
 *   <li>GET /api/journey/today   — 오늘의 1 카드 추천</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/journey")
@RequiredArgsConstructor
public class JourneyController {

    private final GetJourneyUseCase journey;

    @GetMapping("/weekly")
    public ResponseEntity<GetJourneyUseCase.WeeklyJourney> weekly() {
        return ResponseEntity.ok(journey.weekly(RequestContext.currentUserId()));
    }

    @GetMapping("/today")
    public ResponseEntity<GetJourneyUseCase.DailyPick> today() {
        return ResponseEntity.ok(journey.today(RequestContext.currentUserId()));
    }
}
