package github.lms.lemuel.xr.ai.adapter.in.web;

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /api/internal/llm/* — X-Internal-Token 필요. */
@RestController
@RequestMapping("/api/internal/llm")
@RequiredArgsConstructor
public class InternalLlmController {

    private final GenerateLlmResponseUseCase generateUc;

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest req) {
        var r = generateUc.execute(req.purpose(), req.promptKey(), req.variables());
        return ResponseEntity.ok(new GenerateResponse(
                r.text(), r.cached(), r.provider(), r.model(),
                true,                                       // AI 생성물 — 표시광고법 disclosure 의무
                "AI 보조 — 본문은 성경 참조"                  // 클라이언트가 화면에 표시할 라벨
        ));
    }

    public record GenerateRequest(String purpose, String promptKey, Map<String, Object> variables) {}

    /** AI 응답 DTO 에 aiGenerated/aiLabel 항상 포함 — 한국 표시광고법 + ETHICS-LEGAL §AI. */
    public record GenerateResponse(
            String text,
            boolean cached,
            String provider,
            String model,
            boolean aiGenerated,
            String aiLabel
    ) {}
}
