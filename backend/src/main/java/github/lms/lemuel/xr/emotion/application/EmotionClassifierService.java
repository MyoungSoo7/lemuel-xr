package github.lms.lemuel.xr.emotion.application;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 감정 분류 = Python AI 사이드카 (`ai/`) 호출.
 *
 * <p>backend 는 도메인·DB 만 책임지고 LLM 호출은 사이드카에 위임. 사이드카 URL 은
 * 환경변수 {@code AI_BASE_URL} 로 주입 (k8s 에선 lemuel-xr-ai:8000).</p>
 *
 * <p>Caffeine 캐시로 동일 텍스트 재호출 차단 (사이드카 호출 + LLM 토큰 모두 절약).</p>
 */
@Service
@Slf4j
public class EmotionClassifierService implements ClassifyEmotionUseCase {

    private final RestClient ai;

    public EmotionClassifierService(@Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl) {
        this.ai = RestClient.builder().baseUrl(aiBaseUrl).build();
    }

    record AiRequest(String text) {}
    record AiResponse(String emotion, double confidence) {}

    @Override
    @Cacheable(value = "emotion-classify", key = "#rawText")
    public Result classify(String rawText) {
        try {
            AiResponse resp = ai.post()
                    .uri("/classify-emotion")
                    .body(new AiRequest(rawText))
                    .retrieve()
                    .body(AiResponse.class);
            if (resp == null) {
                return new Result(Emotion.CONFUSED, 0.0);
            }
            return new Result(Emotion.fromString(resp.emotion()), resp.confidence());
        } catch (Exception e) {
            log.warn("AI 사이드카 호출 실패, CONFUSED fallback: {}", e.getMessage());
            return new Result(Emotion.CONFUSED, 0.0);
        }
    }
}
