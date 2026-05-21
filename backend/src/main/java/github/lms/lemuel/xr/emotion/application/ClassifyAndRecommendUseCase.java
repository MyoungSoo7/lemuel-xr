package github.lms.lemuel.xr.emotion.application;

import github.lms.lemuel.xr.emotion.adapter.out.persistence.EmotionLogJpaEntity;
import github.lms.lemuel.xr.emotion.adapter.out.persistence.EmotionLogRepository;
import github.lms.lemuel.xr.emotion.domain.Emotion;
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner;
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 텍스트 → 감정 분류 + 트랙 A/B 추천 + 로그 영속 + **위기 키워드 즉시 차단**.
 *
 * <p>흐름:
 * <ol>
 *   <li>CrisisKeywordScanner 로 자살·자해 키워드 즉시 검사 (LLM 호출 전)</li>
 *   <li>매칭 시: safety_alerts 기록 + Telegram 알람 + 응답에 crisisLockoutRequired=true + 위기 자원</li>
 *   <li>매칭 안 됨: 평소대로 AI 분류 + 추천</li>
 * </ol>
 * </p>
 *
 * <p>R1 safety line 의 핵심 — 사용자가 "죽고싶어" 라고 쓰면 위기 자원이 *먼저* 와야 한다.
 * AI 분류 응답이 *대신* 와서는 안 됨.</p>
 */
@Service
@RequiredArgsConstructor
public class ClassifyAndRecommendUseCase {

    private final ClassifyEmotionUseCase classifier;
    private final EmotionRecommender recommender;
    private final EmotionLogRepository logs;
    private final CrisisKeywordScanner scanner;
    private final RecordSafetyAlertUseCase safetyAlert;

    @Transactional
    public Result execute(UUID userId, String text, String preferredMode) {
        // Layer 3 — 위기 키워드 즉시 차단 (LLM 호출 전)
        CrisisKeywordScanner.ScanResult scan = scanner.scan(text);
        if (scan.matched()) {
            var alertResult = safetyAlert.execute(userId, null, "emotion_text", scan);
            // 위기 응답 — AI 분류 응답 자리를 위기 자원이 차지.
            return Result.crisis(scan.severity(), alertResult.shownResources());
        }

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
                topicSuggestions, characterSuggestions,
                false, null, List.of());
    }

    public record Result(
            Long emotionLogId,
            Emotion primary,
            double confidence,
            List<EmotionRecommender.TopicSuggestion> trackA,
            List<EmotionRecommender.CharacterSuggestion> trackB,
            /** R1 safety lockout flag — true 면 클라이언트는 *위기 자원 화면* 강제 표시. */
            boolean crisisLockoutRequired,
            String crisisSeverity,
            List<Map<String, Object>> crisisResources
    ) {
        public static Result crisis(String severity, List<Map<String, Object>> resources) {
            return new Result(null, null, 0.0, List.of(), List.of(),
                    true, severity, resources);
        }
    }
}
