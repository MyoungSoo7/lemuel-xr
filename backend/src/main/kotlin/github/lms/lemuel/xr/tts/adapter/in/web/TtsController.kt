package github.lms.lemuel.xr.tts.adapter.`in`.web

import github.lms.lemuel.xr.tts.application.SynthesizeTtsUseCase
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** `/api/tts/...` — 사용자 클라이언트가 직접 호출 가능. */
@RestController
@RequestMapping("/api/tts")
@Validated
class TtsController(
    private val synthesizeUc: SynthesizeTtsUseCase,
) {

    @PostMapping("/synthesize")
    fun synthesize(@RequestBody req: SynthesizeRequest): ResponseEntity<SynthesizeResponse> {
        val r = synthesizeUc.execute(req.text, req.voiceId, req.speakingRate)
        return ResponseEntity.ok(SynthesizeResponse(r.audioUrl, r.durationMs, r.cached))
    }

    @GetMapping("/voices")
    fun voices(): ResponseEntity<VoicesResponse> =
        ResponseEntity.ok(
            VoicesResponse(
                listOf(
                    mapOf("id" to "narrator-male-low", "label" to "내레이터 (낮음)", "lang" to "ko"),
                    mapOf("id" to "narrator-female-soft", "label" to "내레이터 (부드러움)", "lang" to "ko"),
                    mapOf(
                        "id" to "goliath-bass", "label" to "골리앗", "lang" to "ko",
                        "warning" to "low-frequency threat",
                    ),
                ),
            ),
        )

    data class SynthesizeRequest(
        @field:NotBlank @field:Size(max = 5000) val text: String,
        @field:Size(max = 50) val voiceId: String?,
        val speakingRate: Double?,
    )

    data class SynthesizeResponse(val audioUrl: String?, val durationMs: Int?, val cached: Boolean)

    data class VoicesResponse(val voices: List<Map<String, Any>>)
}
