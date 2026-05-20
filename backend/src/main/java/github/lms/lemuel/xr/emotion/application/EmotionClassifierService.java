package github.lms.lemuel.xr.emotion.application;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Spring AI ChatClient 로 사용자 텍스트 → 7개 감정 중 1개 분류.
 *
 * <p>LLM 응답이 enum 값 1개만 포함하도록 강제 프롬프트.
 * 결과는 Caffeine 캐시로 동일 텍스트 재호출 0 LLM 토큰.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmotionClassifierService implements ClassifyEmotionUseCase {

    private final ChatClient.Builder chatClientBuilder;

    private static final String PROMPT_TEMPLATE = """
            너는 한국어 감정 분류기다. 다음 사용자 텍스트를 7개 감정 중 정확히 하나로 분류하라.

            가능한 감정 (대문자 enum 만 출력):
            ANXIOUS    - 불안, 두려움, 걱정
            SAD        - 슬픔, 우울, 상실
            ANGRY      - 분노, 짜증, 답답함
            CONFUSED   - 혼란, 막막함, 혼동
            LONELY     - 외로움, 고립감
            EXHAUSTED  - 지침, 번아웃, 무기력
            GRATEFUL   - 감사, 평안, 충만

            사용자 텍스트: %s

            출력 형식 (정확히 이 형식, 한 줄): EMOTION|CONFIDENCE
            예: ANXIOUS|0.85

            CONFIDENCE 는 0.0~1.0 의 분류 확신도.
            출력에는 enum 값 + | + 숫자만 포함하라. 설명·여백·따옴표 없음.
            """;

    @Override
    @Cacheable(value = "emotion-classify", key = "#rawText")
    public Result classify(String rawText) {
        String prompt = String.format(PROMPT_TEMPLATE, rawText);
        String response;
        try {
            response = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("ChatClient 호출 실패, CONFUSED fallback: {}", e.getMessage());
            return new Result(Emotion.CONFUSED, 0.0);
        }

        return parse(response);
    }

    static Result parse(String raw) {
        if (raw == null) return new Result(Emotion.CONFUSED, 0.0);
        String trimmed = raw.trim().replace("\"", "").replace("'", "");
        String[] parts = trimmed.split("\\|");
        Emotion emotion = Emotion.fromString(parts[0].trim());
        double confidence = 0.5;
        if (parts.length > 1) {
            try {
                confidence = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[1].trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        return new Result(emotion, confidence);
    }
}
