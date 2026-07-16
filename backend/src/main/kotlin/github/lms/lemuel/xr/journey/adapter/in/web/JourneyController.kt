package github.lms.lemuel.xr.journey.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.journey.application.GetJourneyUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * /api/journey/ — 사용자 일주일 여정 (CROSS-MAPPING-VR-AR.md §4).
 *
 * - GET /api/journey/weekly  — 이번 주 인물·가치·테마
 * - GET /api/journey/today   — 오늘의 1 카드 추천
 */
@RestController
@RequestMapping("/api/journey")
class JourneyController(
    private val journey: GetJourneyUseCase,
) {

    @GetMapping("/weekly")
    fun weekly(): ResponseEntity<GetJourneyUseCase.WeeklyJourney> =
        ResponseEntity.ok(journey.weekly(RequestContext.currentUserId()))

    @GetMapping("/today")
    fun today(): ResponseEntity<GetJourneyUseCase.DailyPick> =
        ResponseEntity.ok(journey.today(RequestContext.currentUserId()))
}
