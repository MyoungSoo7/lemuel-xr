package github.lms.lemuel.xr

import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * CardBookmarkController 통합 테스트 — Seed(ooo) acceptance criteria AC1~4 검증.
 *
 * - AC1: 카드 하트 토글로 담기/빼기 (idempotent)
 * - AC2: '내 북마크' 목록 최신순
 * - AC3: 목록 항목이 원문(scriptureRef)+본문 포함 → 카드 원문 열기 가능
 * - AC4: 같은 게스트 세션 유지 동안 서버에 유지 (익명 guest_id FK)
 *
 * 실 서버(RANDOM_PORT) + Postgres/pgvector Testcontainer + Flyway. 게스트 발급 →
 * disclaimer 동의 흐름은 ContentWebIT 와 동일.
 */
class CardBookmarkWebIT : IntegrationTestBase() {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var topicContents: TopicContentJpaRepository

    private var token: String = ""

    private fun client(): RestClient = RestClient.create("http://localhost:$port")

    private fun authed(t: String = token): RestClient = RestClient.builder()
        .baseUrl("http://localhost:$port")
        .defaultHeader("Authorization", "Bearer $t")
        .build()

    private val mapRef = object : ParameterizedTypeReference<Map<String, Any?>>() {}
    private val listRef = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}

    /** 게스트 발급 + disclaimer 동의 → 토큰 반환. */
    private fun newGuest(): String {
        val rest = client()
        val guest = rest.post().uri("/api/auth/guest")
            .body(
                mapOf(
                    "deviceFingerprint" to "bookmark-it-" + System.nanoTime(),
                    "deviceType" to "quest3",
                ),
            )
            .retrieve().body(mapRef)!!
        val t = guest["token"] as String
        rest.post().uri("/api/auth/accept-disclaimer")
            .header("Authorization", "Bearer $t")
            .body(mapOf<String, Any>())
            .retrieve().body(mapRef)
        return t
    }

    /** 시드된 AR 토픽 카드 id 2개 (BIGSERIAL). */
    private fun twoCardIds(): Pair<Long, Long> {
        val cards = topicContents.findAll()
        assertThat(cards.size).isGreaterThanOrEqualTo(2)
        return cards[0].id!! to cards[1].id!!
    }

    @BeforeEach
    fun setUp() {
        token = newGuest()
        assertThat(token).isNotBlank()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `AC1_2_3 담기 목록 최신순 원문포함 그리고 빼기 토글`() {
        val (cardA, cardB) = twoCardIds()

        // AC1 — 담기
        val added = authed().post().uri("/api/content/bookmarks")
            .body(mapOf("topicContentId" to cardA))
            .retrieve().body(mapRef)!!
        assertThat(added["topicContentId"] as Number).isEqualTo(cardA.toInt())
        assertThat(added["id"]).isNotNull()

        authed().post().uri("/api/content/bookmarks")
            .body(mapOf("topicContentId" to cardB))
            .retrieve().body(mapRef)

        // AC2 — 목록 최신순 (cardB 가 나중 → 맨 위)
        val list1 = authed().get().uri("/api/content/bookmarks").retrieve().body(listRef)!!
        assertThat(list1).hasSize(2)
        assertThat((list1[0]["topicContentId"] as Number).toLong()).isEqualTo(cardB)
        assertThat((list1[1]["topicContentId"] as Number).toLong()).isEqualTo(cardA)

        // AC3 — 원문(scriptureRef)+본문 포함 → 목록에서 카드 원문 열기 가능
        assertThat(list1[0]).containsKey("scriptureRef").containsKey("body").containsKey("title")
        assertThat(list1[0]["body"] as String).isNotBlank()

        // AC1 — 멱등: cardA 재담기 → 중복 없음(여전히 2개)
        authed().post().uri("/api/content/bookmarks")
            .body(mapOf("topicContentId" to cardA))
            .retrieve().body(mapRef)
        val list2 = authed().get().uri("/api/content/bookmarks").retrieve().body(listRef)!!
        assertThat(list2).hasSize(2)

        // AC1 — 빼기(토글): cardA 제거 → 1개
        authed().delete().uri("/api/content/bookmarks/$cardA").retrieve().toBodilessEntity()
        val list3 = authed().get().uri("/api/content/bookmarks").retrieve().body(listRef)!!
        assertThat(list3).hasSize(1)
        assertThat((list3[0]["topicContentId"] as Number).toLong()).isEqualTo(cardB)

        // 빼기 멱등: 이미 없는 것 또 빼도 성공(204)
        authed().delete().uri("/api/content/bookmarks/$cardA").retrieve().toBodilessEntity()
        assertThat(authed().get().uri("/api/content/bookmarks").retrieve().body(listRef)!!).hasSize(1)
    }

    @Test
    fun `AC4 서버 영속 — 새 클라이언트 같은 토큰으로도 조회됨`() {
        val (cardA, _) = twoCardIds()
        authed().post().uri("/api/content/bookmarks")
            .body(mapOf("topicContentId" to cardA))
            .retrieve().body(mapRef)

        // 완전히 새로운 RestClient(동일 게스트 토큰) → 서버 저장이므로 그대로 보임
        val fresh = RestClient.builder()
            .baseUrl("http://localhost:$port")
            .defaultHeader("Authorization", "Bearer $token")
            .build()
        val list = fresh.get().uri("/api/content/bookmarks").retrieve().body(listRef)!!
        assertThat(list).hasSize(1)
        assertThat((list[0]["topicContentId"] as Number).toLong()).isEqualTo(cardA)
    }

    @Test
    fun `게스트 격리 — 다른 게스트 북마크는 안 보임`() {
        val (cardA, _) = twoCardIds()
        authed().post().uri("/api/content/bookmarks")
            .body(mapOf("topicContentId" to cardA))
            .retrieve().body(mapRef)

        val other = newGuest()
        val otherList = authed(other).get().uri("/api/content/bookmarks").retrieve().body(listRef)!!
        assertThat(otherList).isEmpty()
    }

    @Test
    fun `없는 카드 담기 400 E_VALIDATION`() {
        try {
            authed().post().uri("/api/content/bookmarks")
                .body(mapOf("topicContentId" to 999_999_999L))
                .retrieve().body(String::class.java)
            Assertions.fail<Any>("expected 400")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
            assertThat(e.responseBodyAsString).contains("E_VALIDATION")
        }
    }

    @Test
    fun `미인증 목록조회 차단`() {
        try {
            client().get().uri("/api/content/bookmarks").retrieve().body(String::class.java)
            Assertions.fail<Any>("expected auth block")
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode.value()).isIn(401, 403, 451)
        }
    }
}
