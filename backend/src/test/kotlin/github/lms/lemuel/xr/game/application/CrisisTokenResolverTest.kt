package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.application.port.out.CrisisContactPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 시나리오 payload 의 `{{crisis_resources.default}}` 치환 검증.
 *
 * 이 클래스가 없으면 yml 의 토큰이 *문자 그대로* 사용자에게 나간다 — 낡은 번호보다 나쁘다.
 * 그래서 "치환됐다" 뿐 아니라 "원시 토큰이 남아 있지 않다" 를 함께 못 박는다.
 */
class CrisisTokenResolverTest {

    private val token = CrisisTokenResolver.TOKEN

    private fun resolver(contact: String): CrisisTokenResolver =
        CrisisTokenResolver { _, _ -> contact }

    @Test
    fun `최상위 문자열의 토큰을 치환한다`() {
        val out = resolver("109").resolve(mapOf("crisis_reminder" to "지금 무겁다면, $token."))
        assertThat(out["crisis_reminder"]).isEqualTo("지금 무겁다면, 109.")
    }

    @Test
    fun `중첩 map 과 list 안쪽까지 훑는다`() {
        // Scene extras 는 payload 한 단계 안쪽에 있다 — 최상위만 훑으면 위기 문구를 놓친다.
        val payload = mapOf(
            "sceneId" to 3,
            "extras" to mapOf(
                "crisis_reminder" to "$token.",
                "nested" to listOf(
                    "본문보다 $token 연결이 우선이다",
                    mapOf("deep" to "$token 로 연결됩니다"),
                ),
            ),
        )

        val out = resolver("109").resolve(payload)

        assertThat(out.toString()).doesNotContain(token).doesNotContain("{{")
        assertThat(out.toString()).contains("109")
        assertThat(out["sceneId"]).isEqualTo(3)
    }

    @Test
    fun `토큰이 없으면 위기 연락처를 조회하지 않는다`() {
        // 모든 Scene 이 위기 문구를 갖지는 않는다 — 없는 경우까지 DB 를 때리지 않는다.
        var calls = 0
        val port = CrisisContactPort { _, _ -> calls++; "109" }
        val out = CrisisTokenResolver(port).resolve(mapOf("title" to "로뎀나무 아래", "next" to 2))

        assertThat(calls).isZero()
        assertThat(out).containsEntry("title", "로뎀나무 아래").containsEntry("next", 2)
    }

    @Test
    fun `region 과 locale 을 포트로 그대로 넘긴다`() {
        var seen: Pair<String, String>? = null
        val port = CrisisContactPort { r, l -> seen = r to l; "988" }

        val out = CrisisTokenResolver(port).resolve(mapOf("t" to token), "US", "en-US")

        assertThat(seen).isEqualTo("US" to "en-US")
        assertThat(out["t"]).isEqualTo("988")
    }

    @Test
    fun `기본 region 과 locale 은 KR ko-KR`() {
        var seen: Pair<String, String>? = null
        val port = CrisisContactPort { r, l -> seen = r to l; "109" }

        CrisisTokenResolver(port).resolve(mapOf("t" to token))

        assertThat(seen).isEqualTo(CrisisTokenResolver.DEFAULT_REGION to CrisisTokenResolver.DEFAULT_LOCALE)
    }

    @Test
    fun `null 과 비문자열 값은 손대지 않는다`() {
        val out = resolver("109").resolve(mapOf("next" to null, "flag" to true, "n" to 7))
        assertThat(out).containsEntry("next", null).containsEntry("flag", true).containsEntry("n", 7)
    }

    @Test
    fun `포트가 대체값만 줄 수 있는 상황에서도 원시 토큰은 남지 않는다`() {
        // Fail-safe 계약: 포트는 DB 가 죽어도 설정 대체값을 준다(SafetyCrisisContactAdapter).
        // 그 최악의 경로에서도 사용자에게 나가는 문자열에 "{{" 가 남으면 안 된다.
        val out = resolver("109").resolve(
            mapOf("extras" to mapOf("crisis_reminder" to "$token.")),
        )

        @Suppress("UNCHECKED_CAST")
        val reminder = (out["extras"] as Map<String, Any?>)["crisis_reminder"] as String
        assertThat(reminder).doesNotContain("{{").doesNotContain("}}").isEqualTo("109.")
    }
}
