package github.lms.lemuel.xr.tts.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Python TTS 사이드카 (Coqui XTTS-v2). text+voice+rate → wav URL. */
@Component
@Slf4j
public class TtsSidecarClient {

    private final WebClient client;
    private final Duration timeout = Duration.ofSeconds(30);

    public TtsSidecarClient(@Value("${tts.base-url}") String baseUrl) {
        this.client = WebClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public SynthesisResult synthesize(String text, String voiceId, Double speakingRate) {
        try {
            Map<String, Object> resp = client.post()
                    .uri("/synthesize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "text", text,
                            "voiceId", voiceId == null ? "narrator-male-low" : voiceId,
                            "speakingRate", speakingRate == null ? 1.0 : speakingRate
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(timeout)
                    .block();
            if (resp == null) throw new AppException(ErrorCode.E_TTS_UPSTREAM_FAIL, "empty body");
            return new SynthesisResult(
                    (String) resp.get("audioUrl"),
                    (Integer) resp.get("durationMs"),
                    (String) resp.get("engine")
            );
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TTS sidecar 실패: {}", e.getMessage());
            throw new AppException(ErrorCode.E_TTS_UPSTREAM_FAIL, e.getMessage(), e);
        }
    }

    public record SynthesisResult(String audioUrl, Integer durationMs, String engine) {}
}
