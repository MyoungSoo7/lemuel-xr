package github.lms.lemuel.xr.content.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * R2 footer 의 위기 자원 번호-기관명 매핑 회귀 방지.
 *
 * 정본은 `V7__safety_domain.sql` 시드다 — 1393 = 자살예방상담전화,
 * 1577-0199 = 정신건강위기상담전화, 1588-9191 = 한국생명의전화.
 * 이 문구는 위기 상황 사용자가 직접 읽으므로 매핑이 틀리면 잘못된 기관으로 연결된다.
 */
class PracticeSafetyFooterCatalogTest {

    private val catalog = PracticeSafetyFooterCatalog()

    @Test
    fun `Theme 6·7 footer 는 1393 을 자살예방으로 안내한다`() {
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).contains("1393 자살예방")
        }
    }

    @Test
    fun `Theme 6·7 footer 는 1577-0199 를 정신건강위기로 안내한다`() {
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).contains("1577-0199 정신건강위기")
        }
    }

    @Test
    fun `1393 을 생명의전화로 표기하지 않는다`() {
        // 생명의전화는 1588-9191 이다. 과거 이 footer 가 1393·1577-0199 을
        // 서로 바꿔 표기하면서 1393 을 생명의전화로 안내했다.
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).doesNotContain("생명의전화")
        }
    }

    @Test
    fun `R2 보호 문구 자체는 유지된다`() {
        // 안전선(R2) — 완화·제거 금지 (PracticeSafetyFooterCatalog KDoc).
        assertThat(catalog.forTopic(6)).contains("안전이 우선입니다")
        assertThat(catalog.forTopic(7)).contains("안전 확보가 우선입니다")
    }
}
