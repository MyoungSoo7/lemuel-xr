package github.lms.lemuel.xr.ai.adapter.`in`.web

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** /api/internal/llm/ — X-Internal-Token 필요. */
@RestController
@RequestMapping("/api/internal/llm")
class InternalLlmController(
    private val generateUc: GenerateLlmResponseUseCase,
) {

    @PostMapping("/generate")
    fun generate(@RequestBody req: GenerateRequest): ResponseEntity<GenerateResponse> {
        val r = generateUc.execute(req.purpose, req.promptKey, req.variables)
        return ResponseEntity.ok(
            GenerateResponse(
                r.text, r.cached, r.provider, r.model,
                true, // AI 생성물 — 표시광고법 disclosure 의무
                "AI 보조 — 본문은 성경 참조", // 클라이언트가 화면에 표시할 라벨
            ),
        )
    }

    data class GenerateRequest(
        val purpose: String,
        val promptKey: String,
        val variables: Map<String, Any?>,
    )

    /** AI 응답 DTO 에 aiGenerated/aiLabel 항상 포함 — 한국 표시광고법 + ETHICS-LEGAL §AI. */
    data class GenerateResponse(
        val text: String?,
        val cached: Boolean,
        val provider: String?,
        val model: String?,
        val aiGenerated: Boolean,
        val aiLabel: String,
    )
}
