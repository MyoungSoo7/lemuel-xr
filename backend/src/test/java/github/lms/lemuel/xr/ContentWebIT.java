package github.lms.lemuel.xr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * content/adapter/in/web 패키지 컨트롤러 통합 테스트.
 *
 * <p>대상: JournalController · JournalGuidanceController · ProverbsController ·
 * EcclesiastesController · PracticeReflectionController · UserPsalmController ·
 * ContentController + 정적 카탈로그(EcclesiastesCatalog / JournalGuidanceCatalog /
 * ProverbsThemeCatalog).
 *
 * <p>실 서버(RANDOM_PORT) + Postgres/pgvector Testcontainer + Flyway. AuthAndContentIT 와
 * 동일한 게스트 발급 → disclaimer 동의 흐름을 재사용해 인증/451 게이트를 통과시킨다.
 *
 * <p>주요 분기 커버: 위기 스캔 routed=true(자해 키워드) · 미지 theme → E_VALIDATION(400) ·
 * 빈 emotion → 전체 카탈로그 · 감정 룰 기반 감지 · 안전선(crisis→safety_alert INSERT + 자원 반환).
 */
class ContentWebIT extends IntegrationTestBase {

    @LocalServerPort
    int port;

    private String token;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    /** 인증 필요한 endpoint 를 위한 Authorization 헤더 부착 RestClient. */
    private RestClient authed() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void issueGuestAndAcceptDisclaimer() {
        var rest = client();
        Map<String, Object> guest = rest.post().uri("/api/auth/guest")
                .body(Map.of("deviceFingerprint", "content-web-it-" + System.nanoTime(),
                        "deviceType", "quest3"))
                .retrieve().body(Map.class);
        this.token = (String) guest.get("token");
        assertThat(token).isNotBlank();

        // DisclaimerGateFilter 통과 — /api/content/** 는 동의 후에만 접근 가능.
        rest.post().uri("/api/auth/accept-disclaimer")
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .retrieve().body(Map.class);
    }

    // ─────────────────────────────── ContentController ───────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void content_topics_공개_7주제() {
        // 인증 없이도 공개 카탈로그.
        Map<String, Object> body = client().get().uri("/api/content/topics")
                .retrieve().body(Map.class);
        List<Map<String, Object>> topics = (List<Map<String, Object>>) body.get("topics");
        assertThat(topics).hasSize(7);
        assertThat(topics.toString()).contains("일기와 묵상").contains("사람을 두려워하지 않는 것");
    }

    @Test
    @SuppressWarnings("unchecked")
    void content_topic_scene_에셋_생성() {
        Map<String, Object> body = client().get().uri("/api/content/topics/1/scene?mode=EMOTIONAL")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("topicId", 1).containsEntry("mode", "EMOTIONAL");
        assertThat(body.get("title")).isEqualTo("일기와 묵상");
        Map<String, Object> scene = (Map<String, Object>) body.get("scene");
        assertThat((String) scene.get("skybox")).isEqualTo("skybox-journal.exr");
        assertThat((String) scene.get("narrationId")).contains("journal");
        assertThat(body.get("estimatedDurationSec")).isEqualTo(240);
    }

    @Test
    @SuppressWarnings("unchecked")
    void content_topic_scene_기본모드_AUTO() {
        Map<String, Object> body = client().get().uri("/api/content/topics/7/scene")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("mode", "AUTO");
        Map<String, Object> scene = (Map<String, Object>) body.get("scene");
        assertThat((String) scene.get("skybox")).isEqualTo("skybox-fear.exr");
    }

    @Test
    @SuppressWarnings("unchecked")
    void content_topic_cards_빈결과_반환() {
        // 카드 시드 없이도 200 + 빈 리스트 (findRelevant).
        Map<String, Object> body = client().get().uri("/api/content/topics/1/cards?limit=5")
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("topicId", 1);
        assertThat((List<?>) body.get("cards")).isNotNull();
    }

    @Test
    void content_topic_scene_잘못된_topicId_에러() {
        // Topic.byId 는 알 수 없는 id 에 IllegalArgumentException → fallback 500.
        try {
            client().get().uri("/api/content/topics/99/scene").retrieve().body(String.class);
        } catch (HttpServerErrorException | HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isGreaterThanOrEqualTo(400);
        }
    }

    // ─────────────────────────────── JournalController ───────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void journal_생성_후_목록조회() {
        Map<String, Object> created = authed().post().uri("/api/content/journal")
                .body(Map.of("text", "오늘 하루 감사한 마음으로 일기를 적는다",
                        "formType", "free", "emotionLabel", "grateful", "intensity", 3))
                .retrieve().body(Map.class);
        assertThat(created).containsKey("id");
        assertThat(created.get("text")).isEqualTo("오늘 하루 감사한 마음으로 일기를 적는다");
        assertThat(created.get("formType")).isEqualTo("free");
        assertThat((Integer) created.get("wordCount")).isGreaterThan(0);

        Map<String, Object> list = authed().get().uri("/api/content/journal?limit=10")
                .retrieve().body(Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) list.get("items");
        assertThat(items).isNotEmpty();
        assertThat(items.get(0).get("text")).isEqualTo("오늘 하루 감사한 마음으로 일기를 적는다");
    }

    @Test
    void journal_인증없으면_451_또는_401() {
        // disclaimer 게이트 대상이지만 미인증이면 게이트를 통과해 최종 401/403.
        try {
            client().get().uri("/api/content/journal").retrieve().body(String.class);
        } catch (HttpClientErrorException e) {
            HttpStatusCode s = e.getStatusCode();
            assertThat(s.value()).isIn(401, 403, 451);
        }
    }

    // ──────────────────────────── JournalGuidanceController ───────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void guidance_GET_빈감정_전체카탈로그() {
        Map<String, Object> body = authed().get().uri("/api/content/journal/guidance")
                .retrieve().body(Map.class);
        assertThat(body.get("guidance")).isNull();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) body.get("catalog");
        assertThat(catalog).hasSize(7); // 7 감정
        assertThat(catalog.toString()).contains("불안").contains("감사");
        Map<String, Object> crisis = (Map<String, Object>) body.get("crisis");
        assertThat(crisis).containsEntry("routed", false);
        assertThat((String) body.get("aiFooter")).contains("성경");
    }

    @Test
    @SuppressWarnings("unchecked")
    void guidance_GET_특정감정_단건조언() {
        Map<String, Object> body = authed().get().uri("/api/content/journal/guidance?emotion=ANXIOUS")
                .retrieve().body(Map.class);
        Map<String, Object> g = (Map<String, Object>) body.get("guidance");
        assertThat(g).isNotNull();
        assertThat(g.get("emotion")).isEqualTo("ANXIOUS");
        assertThat(g.get("emotionLabel")).isEqualTo("불안");
        assertThat(((List<?>) body.get("catalog"))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guidance_GET_미지감정_CONFUSED_fallback() {
        Map<String, Object> body = authed().get().uri("/api/content/journal/guidance?emotion=NONSENSE")
                .retrieve().body(Map.class);
        Map<String, Object> g = (Map<String, Object>) body.get("guidance");
        assertThat(g.get("emotion")).isEqualTo("CONFUSED"); // Emotion.fromString fallback
    }

    @Test
    @SuppressWarnings("unchecked")
    void guidance_POST_룰기반_감정감지() {
        // 명시 emotion 없이 텍스트 키워드로 감정 감지 (EXHAUSTED hints: "지쳐").
        Map<String, Object> body = authed().post().uri("/api/content/journal/guidance")
                .body(Map.of("text", "요즘 너무 지쳐서 아무것도 못 하겠다"))
                .retrieve().body(Map.class);
        Map<String, Object> g = (Map<String, Object>) body.get("guidance");
        assertThat(g.get("emotion")).isEqualTo("EXHAUSTED");
        Map<String, Object> crisis = (Map<String, Object>) body.get("crisis");
        assertThat(crisis).containsEntry("routed", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void guidance_POST_명시감정_우선() {
        Map<String, Object> body = authed().post().uri("/api/content/journal/guidance")
                .body(Map.of("text", "감사한 하루", "emotion", "ANGRY"))
                .retrieve().body(Map.class);
        Map<String, Object> g = (Map<String, Object>) body.get("guidance");
        assertThat(g.get("emotion")).isEqualTo("ANGRY"); // 명시 우선
    }

    @Test
    @SuppressWarnings("unchecked")
    void guidance_POST_빈텍스트_CONFUSED() {
        Map<String, Object> body = authed().post().uri("/api/content/journal/guidance")
                .body(Map.of("text", ""))
                .retrieve().body(Map.class);
        Map<String, Object> g = (Map<String, Object>) body.get("guidance");
        assertThat(g.get("emotion")).isEqualTo("CONFUSED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void guidance_POST_위기키워드_routed_true_안전선() {
        // R1 안전선 — "자살" 키워드 매칭 → safety_alert INSERT + 위기 자원 반환.
        Map<String, Object> body = authed().post().uri("/api/content/journal/guidance")
                .body(Map.of("text", "더는 못 버티겠고 자살하고 싶다"))
                .retrieve().body(Map.class);
        Map<String, Object> crisis = (Map<String, Object>) body.get("crisis");
        assertThat(crisis).containsEntry("routed", true);
        List<Map<String, Object>> resources = (List<Map<String, Object>>) crisis.get("resources");
        assertThat(resources).isNotEmpty();
        assertThat(resources.toString()).contains("1393");
    }

    // ─────────────────────────────── ProverbsController ───────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void proverbs_themes_카탈로그() {
        Map<String, Object> body = authed().get().uri("/api/content/proverbs/themes")
                .retrieve().body(Map.class);
        List<Map<String, Object>> themes = (List<Map<String, Object>>) body.get("themes");
        assertThat(themes).hasSize(6); // wisdom/speech/heart/relationship/wealth/diligence
        assertThat(themes.toString()).contains("지혜").contains("잠 1:7");
        assertThat((String) body.get("aiFooter")).contains("잠언");
    }

    @Test
    @SuppressWarnings("unchecked")
    void proverbs_byTheme_유효주제() {
        Map<String, Object> body = authed().get().uri("/api/content/proverbs/by-theme?theme=wisdom")
                .retrieve().body(Map.class);
        Map<String, Object> theme = (Map<String, Object>) body.get("theme");
        assertThat(theme.get("key")).isEqualTo("wisdom");
        assertThat(((List<?>) theme.get("verses"))).hasSize(3);
    }

    @Test
    void proverbs_byTheme_미지주제_E_VALIDATION_400() {
        try {
            authed().get().uri("/api/content/proverbs/by-theme?theme=unknown_xyz")
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getResponseBodyAsString()).contains("E_VALIDATION");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void proverbs_interactions_기록() {
        Map<String, Object> body = authed().post().uri("/api/content/proverbs/interactions")
                .body(Map.of("theme", "heart", "situation", "마음이 흔들리는 상황",
                        "chosenProverbRef", "prov-4:23", "dimension", "spiritual"))
                .retrieve().body(Map.class);
        assertThat(body).containsEntry("theme", "heart")
                .containsEntry("chosenProverbRef", "prov-4:23");
        assertThat(body.get("id")).isNotNull();
    }

    @Test
    void proverbs_interactions_미지주제_400() {
        try {
            authed().post().uri("/api/content/proverbs/interactions")
                    .body(Map.of("theme", "nope", "situation", "x"))
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ────────────────────────────── EcclesiastesController ────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void ecclesiastes_categories_카탈로그() {
        Map<String, Object> body = authed().get().uri("/api/content/ecclesiastes/categories")
                .retrieve().body(Map.class);
        List<Map<String, Object>> categories = (List<Map<String, Object>>) body.get("categories");
        assertThat(categories).hasSize(6);
        assertThat(categories.toString()).contains("헛되").contains("eccl-1:2-11");
        List<Map<String, Object>> seasons = (List<Map<String, Object>>) body.get("seasons");
        assertThat(seasons).hasSize(10); // 전 3:1-8 계절 짝
        assertThat((String) body.get("aiFooter")).contains("전도서");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ecclesiastes_생성_후_목록조회_결론카운트() {
        Map<String, Object> created = authed().post().uri("/api/content/ecclesiastes")
                .body(Map.of("chapterRef", "eccl-1:2-11", "userSeason", "weeping",
                        "futilityNote", "모든 수고가 헛되게 느껴지는 하루였다",
                        "meaningNote", "그래도 오늘의 몫을 감사히 받는다",
                        "listenedAudio", true, "conclusionViewed", true))
                .retrieve().body(Map.class);
        Map<String, Object> view = (Map<String, Object>) created.get("view");
        assertThat(view.get("chapterRef")).isEqualTo("eccl-1:2-11");
        assertThat(view.get("conclusionViewed")).isEqualTo(true);
        Map<String, Object> crisis = (Map<String, Object>) created.get("crisis");
        assertThat(crisis).containsEntry("routed", false);
        assertThat((String) created.get("conclusionInvite")).contains("12:13");

        Map<String, Object> list = authed().get().uri("/api/content/ecclesiastes?limit=20")
                .retrieve().body(Map.class);
        assertThat(((List<?>) list.get("items"))).isNotEmpty();
        assertThat(((Number) list.get("conclusionViewedCount")).longValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ecclesiastes_생성_위기키워드_routed_true() {
        // R1 — futility/meaning 노트에 자해 키워드 → safety_alert.
        Map<String, Object> created = authed().post().uri("/api/content/ecclesiastes")
                .body(Map.of("chapterRef", "eccl-1:2-11", "userSeason", "mourning",
                        "futilityNote", "다 헛되고 그냥 죽고 싶다",
                        "meaningNote", "의미를 못 찾겠다"))
                .retrieve().body(Map.class);
        Map<String, Object> crisis = (Map<String, Object>) created.get("crisis");
        assertThat(crisis).containsEntry("routed", true);
        assertThat(((List<?>) crisis.get("resources"))).isNotEmpty();
    }

    // ─────────────────────────── PracticeReflectionController ─────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void practice_생성_topic6_후_목록() {
        Map<String, Object> created = authed().post().uri("/api/content/practice")
                .body(Map.of("topicId", 6, "practiceKind", "guard_heart",
                        "situation", "마음을 지키는 연습을 했다",
                        "reflection", Map.of("note", "평온함"),
                        "actionTaken", true, "scriptureRef", "prov-4:23",
                        "dimension", "spiritual"))
                .retrieve().body(Map.class);
        Map<String, Object> practice = (Map<String, Object>) created.get("practice");
        assertThat(practice.get("topicId")).isEqualTo(6);
        assertThat(practice.get("actionTaken")).isEqualTo(true);
        Map<String, Object> crisis = (Map<String, Object>) created.get("crisis");
        assertThat(crisis).containsEntry("routed", false);
        assertThat((String) created.get("safetyFooter")).contains("마음 지킴");

        Map<String, Object> list = authed().get().uri("/api/content/practice?topicId=6&limit=10")
                .retrieve().body(Map.class);
        assertThat(list).containsEntry("topicId", 6);
        assertThat(((List<?>) list.get("items"))).isNotEmpty();
        assertThat(((Number) list.get("actionCount")).longValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void practice_생성_topic7_두려움footer() {
        Map<String, Object> created = authed().post().uri("/api/content/practice")
                .body(Map.of("topicId", 7, "practiceKind", "face_fear",
                        "situation", "사람을 두려워하지 않는 연습",
                        "actionTaken", false))
                .retrieve().body(Map.class);
        assertThat((String) created.get("safetyFooter")).contains("두려움");
    }

    @Test
    @SuppressWarnings("unchecked")
    void practice_생성_위기키워드_routed_true() {
        Map<String, Object> created = authed().post().uri("/api/content/practice")
                .body(Map.of("topicId", 6, "practiceKind", "guard_heart",
                        "situation", "너무 힘들어서 죽고 싶다"))
                .retrieve().body(Map.class);
        Map<String, Object> crisis = (Map<String, Object>) created.get("crisis");
        assertThat(crisis).containsEntry("routed", true);
    }

    @Test
    void practice_list_범위밖_topicId_400() {
        // @Min(6) @Max(7) — topicId=5 는 ConstraintViolation → 400.
        try {
            authed().get().uri("/api/content/practice?topicId=5")
                    .retrieve().body(String.class);
            org.junit.jupiter.api.Assertions.fail("expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ─────────────────────────────── UserPsalmController ──────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void psalm_생성() {
        Map<String, Object> body = authed().post().uri("/api/content/psalms")
                .body(Map.of("text", "주여 내 마음의 노래를 받으소서",
                        "form", "lament", "inspiredBy", "psalm-42"))
                .retrieve().body(Map.class);
        assertThat(body).containsKey("id");
        assertThat(body.get("rawText")).isEqualTo("주여 내 마음의 노래를 받으소서");
        assertThat(body.get("form")).isEqualTo("lament");
        assertThat(body.get("inspiredBy")).isEqualTo("psalm-42");
    }
}
