package github.lms.lemuel.xr.emotion.adapter.in.web;

import github.lms.lemuel.xr.emotion.application.ClassifyEmotionUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
@Validated
public class EmotionController {

    private final ClassifyEmotionUseCase classifyEmotion;

    record ClassifyRequest(
            @NotBlank @Size(max = 1000) String text
    ) {}

    record ClassifyResponse(String emotion, double confidence) {}

    @PostMapping("/classify")
    public ResponseEntity<ClassifyResponse> classify(@RequestBody ClassifyRequest req) {
        var result = classifyEmotion.classify(req.text());
        return ResponseEntity.ok(new ClassifyResponse(result.emotion().name(), result.confidence()));
    }
}
