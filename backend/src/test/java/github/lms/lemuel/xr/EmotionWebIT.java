package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.common.chatops.TelegramChatOps;
import github.lms.lemuel.xr.emotion.application.ClassifyEmotionUseCase;
import github.lms.lemuel.xr.emotion.domain.Emotion;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * emotion/adapter/in/web/EmotionController 통합 테스트.
 *
 * <p>AI 사이드카 분류({@link ClassifyEmotionUseCase}) 는 {@link MockitoBean} 으로 목킹 — 실제 LLM
 * 호출 없음. TelegramChatOps 도 목킹(테스트 프로필에서 chatops disabled 이지만 방어적).
 * ContentWebIT 의 게스트 발급 → disclaimer 동의 인증 패턴 재사용.
 *
 * <p>커버 분기:
 * <ul>
 *   <li>정상 텍스트 → 분류 + trackA/trackB 추천 응답</li>
 *   <li>위기 키워드("죽고 싶어") → crisisLockout 응답, 분류/추천은 모두 null (R1 safety line)</li>
 *   <li>인증 없으면 401/451, 빈 텍스트 검증 실패 400</li>
 * </ul>
 */
class EmotionWebIT extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @MockitoBean
    ClassifyEmotionUseCase classifier;

    @MockitoBean
    TelegramChatOps chatOps;

    private String token;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private RestClient authed() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @BeforeEach
    void issueGuestAndAcceptDisclaimer() {
        var rest = client();
        Map<?, ?> guest = rest.post().uri("/api/auth/guest")
                .body(Map.of("deviceFingerprint", "emotion-web-it-" + System.nanoTime(),
                        "deviceType", "quest3"))
                .retrieve().body(Map.class);
        this.token = (String) guest.get("token");
        assertThat(token).isNotBlank();
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 정상_텍스트_분류후_추천_응답() {
        when(classifier.classify(any()))
                .thenReturn(new ClassifyEmotionUseCase.Result(Emotion.ANXIOUS, 0.88));

        Map<String, Object> body = authed().post().uri("/api/emotion/classify")
                .body(Map.of("text", "오늘 좀 불안했어요",
                        "context", Map.of("preferredMode", "emotional")))
                .retrieve().body(Map.class);

        assertThat(body.get("crisisLockout")).isNull();
        Map<String, Object> primary = (Map<String, Object>) body.get("primary");
        assertThat(primary).containsEntry("emotion", "ANXIOUS");
        assertThat(((Number) primary.get("confidence")).doubleValue()).isEqualTo(0.88);
        Map<String, Object> recs = (Map<String, Object>) body.get("recommendations");
        assertThat(recs.get("trackA")).isNotNull();
        assertThat(recs.get("trackB").toString()).contains("MOSES");
        assertThat(body.get("emotionLogId")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 위기_키워드_crisisLockout_응답_분류는_null() {
        // 실제 CrisisKeywordScanner regex 매칭 — "죽고 싶" 은 기본 regex 에 포함.
        // 위기 게이트가 AI 분류보다 *먼저* 동작해야 한다 (classifier 는 호출되지 않음).
        Map<String, Object> body = authed().post().uri("/api/emotion/classify")
                .body(Map.of("text", "이제 그만 죽고 싶어요"))
                .retrieve().body(Map.class);

        assertThat(body.get("primary")).isNull();
        assertThat(body.get("recommendations")).isNull();
        Map<String, Object> lockout = (Map<String, Object>) body.get("crisisLockout");
        assertThat(lockout).isNotNull();
        assertThat(lockout).containsEntry("required", true);
        assertThat(lockout).containsEntry("severity", "high");
        assertThat(lockout.get("gentleMessage").toString()).contains("1393");
    }

    @Test
    void 인증_없으면_401_또는_451() {
        try {
            client().post().uri("/api/emotion/classify")
                    .body(Map.of("text", "hi"))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected auth failure");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403, 451);
        }
    }

    @Test
    void 빈_텍스트는_400_검증실패() {
        try {
            authed().post().uri("/api/emotion/classify")
                    .body(Map.of("text", ""))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }
}
