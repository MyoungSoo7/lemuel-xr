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
        return ResponseEntity.ok(new GenerateResponse(r.text(), r.cached(), r.provider(), r.model()));
    }

    public record GenerateRequest(String purpose, String promptKey, Map<String, Object> variables) {}
    public record GenerateResponse(String text, boolean cached, String provider, String model) {}
}
