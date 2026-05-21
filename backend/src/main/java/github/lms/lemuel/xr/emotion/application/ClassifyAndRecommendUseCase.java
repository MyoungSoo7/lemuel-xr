package github.lms.lemuel.xr.emotion.application;

import github.lms.lemuel.xr.emotion.adapter.out.persistence.EmotionLogJpaEntity;
import github.lms.lemuel.xr.emotion.adapter.out.persistence.EmotionLogRepository;
import github.lms.lemuel.xr.emotion.domain.Emotion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 텍스트 → 감정 분류 + 트랙 A/B 추천 + 로그 영속.
 *
 * <p>흐름: classify (AI 사이드카) → recommendations 룰 적용 → emotion_logs INSERT.</p>
 */
@Service
@RequiredArgsConstructor
public class ClassifyAndRecommendUseCase {

    private final ClassifyEmotionUseCase classifier;
    private final EmotionRecommender recommender;
    private final EmotionLogRepository logs;

    @Transactional
    public Result execute(UUID userId, String text, String preferredMode) {
        var classified = classifier.classify(text);
        Emotion emo = classified.emotion();

        var topicSuggestions = recommender.trackA(emo);
        var characterSuggestions = recommender.trackB(emo);

        EmotionLogJpaEntity log = new EmotionLogJpaEntity();
        log.setUserId(userId);
        log.setRawText(text);
        log.setClassifiedEmotion(emo.name());
        log.setConfidence(BigDecimal.valueOf(classified.confidence()).setScale(3, RoundingMode.HALF_UP));
        log.setChosenDimension(preferredMode);
        log.setRecommendedTrack(topicSuggestions.isEmpty() ? "B" : "A");
        if (!topicSuggestions.isEmpty()) {
            log.setRecommendedContent("topic:" + topicSuggestions.get(0).topicId());
        } else if (!characterSuggestions.isEmpty()) {
            log.setRecommendedContent("mission:" + characterSuggestions.get(0).character().toLowerCase());
        }
        log.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        logs.save(log);

        return new Result(log.getId(), emo, classified.confidence(),
                topicSuggestions, characterSuggestions);
    }

    public record Result(
            Long emotionLogId,
            Emotion primary,
            double confidence,
            List<EmotionRecommender.TopicSuggestion> trackA,
            List<EmotionRecommender.CharacterSuggestion> trackB
    ) {}
}
