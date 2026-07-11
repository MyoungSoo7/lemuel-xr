package github.lms.lemuel.xr.emotion.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.emotion.application.ClassifyAndRecommendUseCase;
import github.lms.lemuel.xr.emotion.application.EmotionRecommender;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/emotion/classify — 사용자 텍스트 → 감정 분류 + Track A·B 추천.
 *
 * <p>위기 키워드 감지 시 (R1 safety line) crisisLockout 응답 — 클라이언트는 위기 자원 화면 강제.
 * AI 분류 응답이 *대신* 와서는 안 됨.</p>
 */
@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
@Validated
public class EmotionController {

    private final ClassifyAndRecommendUseCase classifyAndRecommend;

    @PostMapping("/classify")
    @Timed(value = "emotion.classify", percentiles = {0.5, 0.95, 0.99},
           description = "사용자 텍스트 감정 분류 latency — AI 사이드카 호출 + 추천 매핑 포함")
    public ResponseEntity<ClassifyResponse> classify(@Valid @RequestBody ClassifyRequest req) {
        var r = classifyAndRecommend.execute(
                RequestContext.currentUserId(),
                req.text(),
                req.context() == null ? null : req.context().preferredMode()
        );
        if (r.crisisLockoutRequired()) {
            // 위기 응답 — AI 분류·추천은 모두 null. 클라이언트는 crisisLockout 만 봄.
            return ResponseEntity.ok(new ClassifyResponse(
                    null, null, null,
                    new CrisisLockoutDto(true, r.crisisSeverity(), r.crisisResources(),
                            "지금 이 순간 당신과 함께 있는 사람이 있습니다. 1393 (자살예방상담전화) 또는 위 자원으로 연결됩니다.")
            ));
        }
        return ResponseEntity.ok(new ClassifyResponse(
                r.emotionLogId(),
                new EmotionDto(r.primary().name(), r.confidence()),
                new RecommendationsDto(r.trackA(), r.trackB()),
                null
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
            RecommendationsDto recommendations,
            /** R1 safety lockout — null 이면 정상 응답. non-null 이면 클라이언트는 위기 화면 강제. */
            CrisisLockoutDto crisisLockout
    ) {}

    public record EmotionDto(String emotion, double confidence) {}

    public record RecommendationsDto(
            List<EmotionRecommender.TopicSuggestion> trackA,
            List<EmotionRecommender.CharacterSuggestion> trackB
    ) {}

    public record CrisisLockoutDto(
            boolean required,
            String severity,
            List<Map<String, Object>> resources,
            String gentleMessage
    ) {}
}
