package github.lms.lemuel.xr.safety.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.game.application.ExitSessionUseCase
import github.lms.lemuel.xr.safety.application.GetCrisisResourcesUseCase
import github.lms.lemuel.xr.safety.application.port.out.SafetyMetricsPort
import github.lms.lemuel.xr.safety.domain.CrisisResource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

/**
 * /api/safety/ — 위기 자원 카탈로그 + 게임 세션 emergency exit.
 *
 * 크라이시스 자원 GET 은 인증 불필요 — 가장 절박한 순간에 빠른 접근을 보장.
 * lemuel-xr-mental-health-safety skill 의 R1 safety line 따름.
 *
 * 메트릭은 [SafetyMetricsPort] 뒤로 위임한다 — 컨트롤러는 Micrometer 를 직접 알지 않는다 (DIP).
 */
@RestController
class SafetyController(
    private val crisisResourcesUc: GetCrisisResourcesUseCase,
    private val exitUc: ExitSessionUseCase,
    private val metrics: SafetyMetricsPort,
) {

    @GetMapping("/api/safety/crisis-resources")
    fun resources(
        @RequestParam(defaultValue = "KR") region: String,
        @RequestParam(defaultValue = "ko-KR") locale: String,
    ): ResponseEntity<CrisisResponse> {
        val items = crisisResourcesUc.execute(region, locale).map(::toDto)
        return ResponseEntity.ok(CrisisResponse(items))
    }

    @PostMapping("/api/game/sessions/{sid}/exit")
    fun exit(
        @PathVariable("sid") sid: UUID,
        @RequestBody req: ExitRequest,
    ): ResponseEntity<ExitResponse> {
        // /exit 은 `/api/safety/crisis-resources` 와 달리 permitAll 이 아니다(SecurityConfig).
        // 즉 여기 도달한 요청은 이미 JWT 를 통과했고 userId 가 반드시 있다.
        val r = exitUc.execute(RequestContext.currentUserId(), sid, req.reason, req.atSceneId)
        // emergency exit 사유별 메트릭 — Grafana 안전 row 에서 추세 추적.
        metrics.recordSessionExit(req.reason)
        return ResponseEntity.ok(
            ExitResponse(
                r.sessionId,
                r.exitedAt,
                "잠시 멈추셨네요. 다음에 다시 만나요. 시편 23편 음성을 함께 두고 갈게요.",
                null, // softLandingAudioUrl 은 TTS 사이드카 비동기 생성 후 결정
            ),
        )
    }

    private fun toDto(r: CrisisResource): ResourceDto =
        ResourceDto(r.name, r.contactType, r.contactValue, r.description, r.hours, r.category)

    data class CrisisResponse(val resources: List<ResourceDto>)

    data class ResourceDto(
        val name: String,
        val contactType: String,
        val contactValue: String,
        val description: String?,
        val hours: String?,
        val category: String?,
    )

    data class ExitRequest(val reason: String?, val atSceneId: Int?)

    data class ExitResponse(
        val sessionId: UUID?,
        val exitedAt: LocalDateTime?,
        val gentleMessage: String,
        val softLandingAudioUrl: String?,
    )
}
