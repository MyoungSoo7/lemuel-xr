package github.lms.lemuel.xr.emotion.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.emotion.application.ClassifyAndRecommendUseCase;
import github.lms.lemuel.xr.emotion.application.EmotionRecommender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** /api/emotion/classify — 사용자 텍스트 → 감정 분류 + Track A·B 추천 (BACKEND-API-DESIGN §4). */
@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
@Validated
public class EmotionController {

    private final ClassifyAndRecommendUseCase classifyAndRecommend;

    @PostMapping("/classify")
    public ResponseEntity<ClassifyResponse> classify(@RequestBody ClassifyRequest req) {
        var r = classifyAndRecommend.execute(
                RequestContext.currentUserId(),
                req.text(),
                req.context() == null ? null : req.context().preferredMode()
        );
        return ResponseEntity.ok(new ClassifyResponse(
                r.emotionLogId(),
                new EmotionDto(r.primary().name(), r.confidence()),
                new RecommendationsDto(r.trackA(), r.trackB())
        ));
    }

    public record ClassifyRequest(
            @NotBlank @Size(max = 1000) String text,
            ClassifyContext context
    ) {}

    public record ClassifyContext(List<String> previousEmotions, String preferredMode) {}

    public record ClassifyResponse(
            Long emotionLogId,
            EmotionDto primary,
            RecommendationsDto recommendations
    ) {}

    public record EmotionDto(String emotion, double confidence) {}

    public record RecommendationsDto(
            List<EmotionRecommender.TopicSuggestion> trackA,
            List<EmotionRecommender.CharacterSuggestion> trackB
    ) {}
}
