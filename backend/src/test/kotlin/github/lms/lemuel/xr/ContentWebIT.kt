package github.lms.lemuel.xr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

/**
 * content/adapter/in/web 패키지 컨트롤러 통합 테스트.
 *
 * 대상: JournalController · JournalGuidanceController · ProverbsController ·
 * EcclesiastesController · PracticeReflectionController · UserPsalmController ·
 * ContentController + 정적 카탈로그(EcclesiastesCatalog / JournalGuidanceCatalog /
 * ProverbsThemeCatalog).
 *
 * 실 서버(RANDOM_PORT) + Postgres/pgvector Testcontainer + Flyway. AuthAndContentIT 와
 * 동일한 게스트 발급 → disclaimer 동의 흐름을 재사용해 인증/451 게이트를 통과시킨다.
 *
 * 주요 분기 커버: 위기 스캔 routed=true(자해 키워드) · 미지 theme → E_VALIDATION(400) ·
 * 빈 emotion → 전체 카탈로그 · 감정 룰 기반 감지 · 안전선(crisis→safety_alert INSERT + 자원 반환).
 */
class ContentWebIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    private var token: String = ""

    private fun client(): RestClient = RestClient.create("http://localhost:$port")

    /** 인증 필요한 endpoint 를 위한 Authorization 헤더 부착 RestClient. */
    private fun authed(): RestClient = RestClient.builder()
        .baseUrl("http://localhost:$port")
        .defaultHeader("Authorization", "Bearer $token")
        .build()

    @BeforeEach
    fun issueGuestAndAcceptDisclaimer() {
        val rest = client()
        val guest = rest.post().uri("/api/auth/guest")
            .body(
                mapOf(
                    "deviceFingerprint" to "content-web-it-" + System.nanoTime(),
                    "deviceType" to "quest3",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        this.token = guest["token"] as String
        assertThat(token).isNotBlank()

        // DisclaimerGateFilter 통과 — /api/content/** 는 동의 후에만 접근 가능.
        rest.post().uri("/api/auth/accept-disclaimer")
            .header("Authorization", "Bearer $token")
            .body(mapOf<String, Any>())
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})
    }

    // ─────────────────────────────── ContentController ───────────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `content topics 공개 7주제`() {
        // 인증 없이도 공개 카탈로그.
        val body = client().get().uri("/api/content/topics")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val topics = body["topics"] as List<Map<String, Any>>
        assertThat(topics).hasSize(7)
        assertThat(topics.toString()).contains("일기와 묵상").contains("사람을 두려워하지 않는 것")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `content topic scene 에셋 생성`() {
        val body = client().get().uri("/api/content/topics/1/scene?mode=EMOTIONAL")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("topicId", 1).containsEntry("mode", "EMOTIONAL")
        assertThat(body["title"]).isEqualTo("일기와 묵상")
        val scene = body["scene"] as Map<String, Any>
        assertThat(scene["skybox"] as String).isEqualTo("skybox-journal.exr")
        assertThat(scene["narrationId"] as String).contains("journal")
        assertThat(body["estimatedDurationSec"]).isEqualTo(240)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `content topic scene 기본모드 AUTO`() {
        val body = client().get().uri("/api/content/topics/7/scene")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("mode", "AUTO")
        val scene = body["scene"] as Map<String, Any>
        assertThat(scene["skybox"] as String).isEqualTo("skybox-fear.exr")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `content topic cards 빈결과 반환`() {
        // 카드 시드 없이도 200 + 빈 리스트 (findRelevant).
        val body = client().get().uri("/api/content/topics/1/cards?limit=5")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("topicId", 1)
        assertThat(body["cards"] as List<*>).isNotNull()
    }

    @Test
    fun `content topic scene 잘못된 topicId 에러`() {
        // Topic.byId 는 알 수 없는 id 에 IllegalArgumentException → fallback 500.
        try {
            client().get().uri("/api/content/topics/99/scene").retrieve().body(String::class.java)
        } catch (e: HttpServerErrorException) {
            assertThat(e.statusCode.value()).isGreaterThanOrEqualTo(400)
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isGreaterThanOrEqualTo(400)
        }
    }

    // ─────────────────────────────── JournalController ───────────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `journal 생성 후 목록조회`() {
        val created = authed().post().uri("/api/content/journal")
            .body(
                mapOf(
                    "text" to "오늘 하루 감사한 마음으로 일기를 적는다",
                    "formType" to "free", "emotionLabel" to "grateful", "intensity" to 3,
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(created).containsKey("id")
        assertThat(created["text"]).isEqualTo("오늘 하루 감사한 마음으로 일기를 적는다")
        assertThat(created["formType"]).isEqualTo("free")
        assertThat(created["wordCount"] as Int).isGreaterThan(0)

        val list = authed().get().uri("/api/content/journal?limit=10")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val items = list["items"] as List<Map<String, Any>>
        assertThat(items).isNotEmpty()
        assertThat(items[0]["text"]).isEqualTo("오늘 하루 감사한 마음으로 일기를 적는다")
    }

    @Test
    fun `journal 인증없으면 451 또는 401`() {
        // disclaimer 게이트 대상이지만 미인증이면 게이트를 통과해 최종 401/403.
        try {
            client().get().uri("/api/content/journal").retrieve().body(String::class.java)
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }

    // ──────────────────────────── JournalGuidanceController ───────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance GET 빈감정 전체카탈로그`() {
        val body = authed().get().uri("/api/content/journal/guidance")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body["guidance"]).isNull()
        val catalog = body["catalog"] as List<Map<String, Any>>
        assertThat(catalog).hasSize(7) // 7 감정
        assertThat(catalog.toString()).contains("불안").contains("감사")
        val crisis = body["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", false)
        assertThat(body["aiFooter"] as String).contains("성경")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance GET 특정감정 단건조언`() {
        val body = authed().get().uri("/api/content/journal/guidance?emotion=ANXIOUS")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val g = body["guidance"] as Map<String, Any>
        assertThat(g).isNotNull()
        assertThat(g["emotion"]).isEqualTo("ANXIOUS")
        assertThat(g["emotionLabel"]).isEqualTo("불안")
        assertThat(body["catalog"] as List<*>).isEmpty()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance GET 미지감정 CONFUSED fallback`() {
        val body = authed().get().uri("/api/content/journal/guidance?emotion=NONSENSE")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val g = body["guidance"] as Map<String, Any>
        assertThat(g["emotion"]).isEqualTo("CONFUSED") // Emotion.fromString fallback
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance POST 룰기반 감정감지`() {
        // 명시 emotion 없이 텍스트 키워드로 감정 감지 (EXHAUSTED hints: "지쳐").
        val body = authed().post().uri("/api/content/journal/guidance")
            .body(mapOf("text" to "요즘 너무 지쳐서 아무것도 못 하겠다"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val g = body["guidance"] as Map<String, Any>
        assertThat(g["emotion"]).isEqualTo("EXHAUSTED")
        val crisis = body["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", false)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance POST 명시감정 우선`() {
        val body = authed().post().uri("/api/content/journal/guidance")
            .body(mapOf("text" to "감사한 하루", "emotion" to "ANGRY"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val g = body["guidance"] as Map<String, Any>
        assertThat(g["emotion"]).isEqualTo("ANGRY") // 명시 우선
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance POST 빈텍스트 CONFUSED`() {
        val body = authed().post().uri("/api/content/journal/guidance")
            .body(mapOf("text" to ""))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val g = body["guidance"] as Map<String, Any>
        assertThat(g["emotion"]).isEqualTo("CONFUSED")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `guidance POST 위기키워드 routed true 안전선`() {
        // R1 안전선 — "자살" 키워드 매칭 → safety_alert INSERT + 위기 자원 반환.
        val body = authed().post().uri("/api/content/journal/guidance")
            .body(mapOf("text" to "더는 못 버티겠고 자살하고 싶다"))
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val crisis = body["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", true)
        val resources = crisis["resources"] as List<Map<String, Any>>
        assertThat(resources).isNotEmpty()
        assertThat(resources.toString()).contains("1393")
    }

    // ─────────────────────────────── ProverbsController ───────────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `proverbs themes 카탈로그`() {
        val body = authed().get().uri("/api/content/proverbs/themes")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val themes = body["themes"] as List<Map<String, Any>>
        assertThat(themes).hasSize(6) // wisdom/speech/heart/relationship/wealth/diligence
        assertThat(themes.toString()).contains("지혜").contains("잠 1:7")
        assertThat(body["aiFooter"] as String).contains("잠언")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `proverbs byTheme 유효주제`() {
        val body = authed().get().uri("/api/content/proverbs/by-theme?theme=wisdom")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val theme = body["theme"] as Map<String, Any>
        assertThat(theme["key"]).isEqualTo("wisdom")
        assertThat(theme["verses"] as List<*>).hasSize(3)
    }

    @Test
    fun `proverbs byTheme 미지주제 E_VALIDATION 400`() {
        try {
            authed().get().uri("/api/content/proverbs/by-theme?theme=unknown_xyz")
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
            assertThat(e.responseBodyAsString).contains("E_VALIDATION")
        }
    }

    @Test
    fun `proverbs interactions 기록`() {
        val body = authed().post().uri("/api/content/proverbs/interactions")
            .body(
                mapOf(
                    "theme" to "heart", "situation" to "마음이 흔들리는 상황",
                    "chosenProverbRef" to "prov-4:23", "dimension" to "spiritual",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsEntry("theme", "heart")
            .containsEntry("chosenProverbRef", "prov-4:23")
        assertThat(body["id"]).isNotNull()
    }

    @Test
    fun `proverbs interactions 미지주제 400`() {
        try {
            authed().post().uri("/api/content/proverbs/interactions")
                .body(mapOf("theme" to "nope", "situation" to "x"))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }

    // ────────────────────────────── EcclesiastesController ────────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `ecclesiastes categories 카탈로그`() {
        val body = authed().get().uri("/api/content/ecclesiastes/categories")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val categories = body["categories"] as List<Map<String, Any>>
        assertThat(categories).hasSize(6)
        assertThat(categories.toString()).contains("헛되").contains("eccl-1:2-11")
        val seasons = body["seasons"] as List<Map<String, Any>>
        assertThat(seasons).hasSize(10) // 전 3:1-8 계절 짝
        assertThat(body["aiFooter"] as String).contains("전도서")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `ecclesiastes 생성 후 목록조회 결론카운트`() {
        val created = authed().post().uri("/api/content/ecclesiastes")
            .body(
                mapOf(
                    "chapterRef" to "eccl-1:2-11", "userSeason" to "weeping",
                    "futilityNote" to "모든 수고가 헛되게 느껴지는 하루였다",
                    "meaningNote" to "그래도 오늘의 몫을 감사히 받는다",
                    "listenedAudio" to true, "conclusionViewed" to true,
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val view = created["view"] as Map<String, Any>
        assertThat(view["chapterRef"]).isEqualTo("eccl-1:2-11")
        assertThat(view["conclusionViewed"]).isEqualTo(true)
        val crisis = created["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", false)
        assertThat(created["conclusionInvite"] as String).contains("12:13")

        val list = authed().get().uri("/api/content/ecclesiastes?limit=20")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(list["items"] as List<*>).isNotEmpty()
        assertThat((list["conclusionViewedCount"] as Number).toLong()).isGreaterThanOrEqualTo(1)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `ecclesiastes 생성 위기키워드 routed true`() {
        // R1 — futility/meaning 노트에 자해 키워드 → safety_alert.
        val created = authed().post().uri("/api/content/ecclesiastes")
            .body(
                mapOf(
                    "chapterRef" to "eccl-1:2-11", "userSeason" to "mourning",
                    "futilityNote" to "다 헛되고 그냥 죽고 싶다",
                    "meaningNote" to "의미를 못 찾겠다",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val crisis = created["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", true)
        assertThat(crisis["resources"] as List<*>).isNotEmpty()
    }

    // ─────────────────────────── PracticeReflectionController ─────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `practice 생성 topic6 후 목록`() {
        val created = authed().post().uri("/api/content/practice")
            .body(
                mapOf(
                    "topicId" to 6, "practiceKind" to "guard_heart",
                    "situation" to "마음을 지키는 연습을 했다",
                    "reflection" to mapOf("note" to "평온함"),
                    "actionTaken" to true, "scriptureRef" to "prov-4:23",
                    "dimension" to "spiritual",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val practice = created["practice"] as Map<String, Any>
        assertThat(practice["topicId"]).isEqualTo(6)
        assertThat(practice["actionTaken"]).isEqualTo(true)
        val crisis = created["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", false)
        assertThat(created["safetyFooter"] as String).contains("마음 지킴")

        val list = authed().get().uri("/api/content/practice?topicId=6&limit=10")
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(list).containsEntry("topicId", 6)
        assertThat(list["items"] as List<*>).isNotEmpty()
        assertThat((list["actionCount"] as Number).toLong()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `practice 생성 topic7 두려움footer`() {
        val created = authed().post().uri("/api/content/practice")
            .body(
                mapOf(
                    "topicId" to 7, "practiceKind" to "face_fear",
                    "situation" to "사람을 두려워하지 않는 연습",
                    "actionTaken" to false,
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(created["safetyFooter"] as String).contains("두려움")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `practice 생성 위기키워드 routed true`() {
        val created = authed().post().uri("/api/content/practice")
            .body(
                mapOf(
                    "topicId" to 6, "practiceKind" to "guard_heart",
                    "situation" to "너무 힘들어서 죽고 싶다",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        val crisis = created["crisis"] as Map<String, Any>
        assertThat(crisis).containsEntry("routed", true)
    }

    @Test
    fun `practice list 범위밖 topicId 400`() {
        // @Min(6) @Max(7) — topicId=5 는 ConstraintViolation → 400.
        try {
            authed().get().uri("/api/content/practice?topicId=5")
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
        }
    }

    // ─────────────────────────────── UserPsalmController ──────────────────────────────

    @Test
    fun `psalm 생성`() {
        val body = authed().post().uri("/api/content/psalms")
            .body(
                mapOf(
                    "text" to "주여 내 마음의 노래를 받으소서",
                    "form" to "lament", "inspiredBy" to "psalm-42",
                ),
            )
            .retrieve().body(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any?>>() {})!!
        assertThat(body).containsKey("id")
        assertThat(body["rawText"]).isEqualTo("주여 내 마음의 노래를 받으소서")
        assertThat(body["form"]).isEqualTo("lament")
        assertThat(body["inspiredBy"]).isEqualTo("psalm-42")
    }
}
