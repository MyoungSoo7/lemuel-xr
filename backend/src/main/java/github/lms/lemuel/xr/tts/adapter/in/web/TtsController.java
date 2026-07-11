package github.lms.lemuel.xr.tts.adapter.in.web;

import github.lms.lemuel.xr.tts.application.SynthesizeTtsUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** /api/tts/* — 사용자 클라이언트가 직접 호출 가능. */
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@Validated
public class TtsController {

    private final SynthesizeTtsUseCase synthesizeUc;

    @PostMapping("/synthesize")
    public ResponseEntity<SynthesizeResponse> synthesize(@RequestBody SynthesizeRequest req) {
        var r = synthesizeUc.execute(req.text(), req.voiceId(), req.speakingRate());
        return ResponseEntity.ok(new SynthesizeResponse(r.audioUrl(), r.durationMs(), r.cached()));
    }

    @GetMapping("/voices")
    public ResponseEntity<VoicesResponse> voices() {
        return ResponseEntity.ok(new VoicesResponse(List.of(
                Map.of("id", "narrator-male-low", "label", "내레이터 (낮음)", "lang", "ko"),
                Map.of("id", "narrator-female-soft", "label", "내레이터 (부드러움)", "lang", "ko"),
                Map.of("id", "goliath-bass", "label", "골리앗", "lang", "ko",
                        "warning", "low-frequency threat")
        )));
    }

    public record SynthesizeRequest(
            @NotBlank @Size(max = 5000) String text,
            @Size(max = 50) String voiceId,
            Double speakingRate
    ) {}

    public record SynthesizeResponse(String audioUrl, Integer durationMs, boolean cached) {}

    public record VoicesResponse(List<Map<String, Object>> voices) {}
}
