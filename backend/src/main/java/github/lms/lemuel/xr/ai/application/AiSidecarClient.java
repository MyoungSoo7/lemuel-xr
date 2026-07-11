package github.lms.lemuel.xr.ai.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Python AI 사이드카 (FastAPI + LangChain) HTTP 클라이언트.
 *
 * <p>WebClient 사용 — 동기 호출은 .block() 으로 마무리. timeout 은 application.yml.
 * 키 보호: provider API 키는 사이드카에만, backend 는 모름.</p>
 */
@Component
@Slf4j
public class AiSidecarClient {

    private final WebClient client;
    private final Duration timeout;
    private final String internalToken;

    public AiSidecarClient(
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.timeout-ms:30000}") long timeoutMs,
            @Value("${ai.internal-token}") String internalToken) {
        this.client = WebClient.builder().baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.internalToken = internalToken;
    }

    /** /ai/generate — promptKey + variables 로 응답 생성. */
    @SuppressWarnings("unchecked")
    public GenerateResult generate(String purpose, String promptKey, Map<String, Object> variables) {
        try {
            Map<String, Object> resp = client.post()
                    .uri("/ai/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalToken)
                    .bodyValue(Map.of(
                            "purpose", purpose,
                            "promptKey", promptKey,
                            "variables", variables
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(timeout)
                    .block();
            if (resp == null) throw new AppException(ErrorCode.E_LLM_UPSTREAM_FAIL, "empty body");
            return new GenerateResult(
                    (String) resp.get("text"),
                    (String) resp.get("provider"),
                    (String) resp.get("model"),
                    (Integer) resp.get("promptTokens"),
                    (Integer) resp.get("completionTokens"),
                    Boolean.TRUE.equals(resp.get("cached"))
            );
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI sidecar /generate 실패 — purpose={} promptKey={}: {}",
                    purpose, promptKey, e.getMessage());
            throw new AppException(ErrorCode.E_LLM_UPSTREAM_FAIL, e.getMessage(), e);
        }
    }

    /** /classify-emotion — 이미 EmotionClassifierService 가 사용 중. 호환 유지. */
    public Map<String, Object> classifyEmotion(String text) {
        try {
            return client.post()
                    .uri("/classify-emotion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(timeout)
                    .block();
        } catch (Exception e) {
            throw new AppException(ErrorCode.E_LLM_UPSTREAM_FAIL, e.getMessage(), e);
        }
    }

    public record GenerateResult(
            String text, String provider, String model,
            Integer promptTokens, Integer completionTokens, boolean cached
    ) {}
}
