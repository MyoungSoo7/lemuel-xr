package github.lms.lemuel.xr.recovery.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.recovery.application.GetRecoveryMetricsUseCase
import github.lms.lemuel.xr.recovery.application.GetRecoveryMetricsUseCase.MetricDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** /api/users/me/recovery — 본인 30일 회복 추이 */
@RestController
class RecoveryController(
    private val getRecoveryMetrics: GetRecoveryMetricsUseCase,
) {

    @GetMapping("/api/users/me/recovery")
    fun recent(@RequestParam(defaultValue = "30") days: Int): ResponseEntity<RecoveryResponse> {
        val items = getRecoveryMetrics.execute(RequestContext.currentUserId(), days)
        return ResponseEntity.ok(RecoveryResponse(items))
    }

    data class RecoveryResponse(val items: List<MetricDto>)
}
