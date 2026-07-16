package github.lms.lemuel.xr.game.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.game.application.CompleteGameSessionUseCase
import github.lms.lemuel.xr.game.application.DecideSceneUseCase
import github.lms.lemuel.xr.game.application.StartGameSessionUseCase
import github.lms.lemuel.xr.game.application.port.out.GameMetricsPort
import github.lms.lemuel.xr.game.domain.Character
import io.micrometer.core.annotation.Timed
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

/**
 * /api/game/{character}/ — 4 인물 generic. character ∈ {joseph, moses, david, jesus}.
 *
 * 기존 /api/game/joseph/ 는 JosephGameController 에 alias 로 유지 (deprecated).
 */
@RestController
@RequestMapping("/api/game/{character}")
class GameController(
    private val startUc: StartGameSessionUseCase,
    private val decideUc: DecideSceneUseCase,
    private val completeUc: CompleteGameSessionUseCase,
    private val metrics: GameMetricsPort,
) {

    @PostMapping("/start")
    @Timed(value = "game.start", extraTags = ["endpoint", "start"], percentiles = [0.5, 0.95, 0.99])
    fun start(
        @PathVariable character: String,
        @RequestBody req: StartRequest,
    ): ResponseEntity<StartResponse> {
        val c = Character.from(character)
        val r = startUc.execute(
            RequestContext.currentUserId(),
            c,
            StartGameSessionUseCase.Input(
                req.mode,
                if (req.client == null) RequestContext.currentDeviceType() else req.client.deviceType,
                req.client?.capabilities,
                req.linkedEmotionLogId,
            ),
        )
        metrics.sessionStarted(c.dbValue, req.mode)
        return ResponseEntity.ok(
            StartResponse(
                r.sessionId, c.dbValue, r.currentScene, r.totalScenes,
                r.appliedMode, r.scenePayload,
            ),
        )
    }

    @PostMapping("/{sid}/decide")
    @Timed(
        value = "game.decide", percentiles = [0.5, 0.95, 0.99],
        description = "Scene 결정 처리 latency — LLM 실시간 호출 시 길어짐",
    )
    fun decide(
        @PathVariable character: String,
        @PathVariable("sid") sid: UUID,
        @RequestBody req: DecideRequest,
    ): ResponseEntity<DecideResponse> {
        val c = Character.from(character)
        val r = decideUc.execute(
            sid, c,
            DecideSceneUseCase.Input(
                req.sceneId, req.decision, req.interactionMeta, req.mode,
            ),
        )
        return ResponseEntity.ok(
            DecideResponse(
                r.sessionId, r.previousScene, r.currentScene,
                r.scenePayload, r.responseText,
            ),
        )
    }

    @PostMapping("/{sid}/complete")
    fun complete(
        @PathVariable character: String,
        @PathVariable("sid") sid: UUID,
        @RequestBody req: CompleteRequest,
    ): ResponseEntity<CompleteResponse> {
        val c = Character.from(character) // 검증
        val r = completeUc.execute(sid, req.finalOutcome, req.closingMessage)
        metrics.sessionCompleted(c.dbValue)
        return ResponseEntity.ok(
            CompleteResponse(
                r.sessionId, r.completedAt, r.durationSeconds,
                r.valuePrompt,
            ),
        )
    }

    // --- DTOs ---

    data class StartRequest(
        val mode: String?,
        val client: ClientCapabilities?,
        val linkedEmotionLogId: Long?,
    )

    data class ClientCapabilities(
        val deviceType: String?,
        val capabilities: Map<String, Any?>?,
    )

    data class StartResponse(
        val sessionId: UUID,
        val character: String,
        val currentScene: Int,
        val totalScenes: Int,
        val appliedMode: String?,
        val scenePayload: Map<String, Any?>,
    )

    data class DecideRequest(
        val sceneId: Int,
        val decision: Map<String, Any?>?,
        val interactionMeta: Map<String, Any?>?,
        val mode: String?,
    )

    data class DecideResponse(
        val sessionId: UUID,
        val previousScene: Int,
        val currentScene: Int,
        val scenePayload: Map<String, Any?>,
        /** Phase 2-A — 직전 결정에 대한 정적 모놀로그/아웃컴 텍스트 (yml lookup). null 가능. */
        val responseText: String?,
    )

    data class CompleteRequest(val finalOutcome: String?, val closingMessage: String?)

    data class CompleteResponse(
        val sessionId: UUID,
        val completedAt: LocalDateTime?,
        val durationSeconds: Int?,
        /** VR→AR 연계: 이 인물이 빛내는 AR 가치 1~7 + 실천 prompt. null 가능. */
        val valuePrompt: CompleteGameSessionUseCase.ValuePrompt?,
    )
}
