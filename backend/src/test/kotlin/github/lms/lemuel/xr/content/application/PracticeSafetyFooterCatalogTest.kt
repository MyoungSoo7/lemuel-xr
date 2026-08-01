package github.lms.lemuel.xr.content.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * R2 footer 의 위기 자원 번호-기관명 매핑 회귀 방지.
 *
 * 정본은 `V20260802031449__crisis_resource_109_canonical.sql` 시드다 —
 * **109 = 자살예방 상담전화** (2024-01-01 보건복지부 통합), 1577-0199 = 정신건강위기상담전화,
 * 1588-9191 = 한국생명의전화.
 *
 * 과거 이 테스트는 `1393 = 자살예방상담전화` 를 정본으로 못 박고 있었다(#13).
 * 1393 은 2024-01-01 자로 109 에 통합되며 자살예방 상담 정번호의 지위를 잃은 번호다.
 * 이 문구는 위기 상황 사용자가 직접 읽으므로 매핑이 틀리면 잘못된 안내가 된다.
 */
class PracticeSafetyFooterCatalogTest {

    private val catalog = PracticeSafetyFooterCatalog()

    @Test
    fun `Theme 6·7 footer 는 109 를 자살예방으로 안내한다`() {
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).contains("109 자살예방")
        }
    }

    @Test
    fun `Theme 6·7 footer 는 구 번호 1393 을 안내하지 않는다`() {
        // 1393 이 불통이라는 근거는 없다. 다만 *자살예방 상담 정번호* 로 안내해서는 안 된다.
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).doesNotContain("1393")
        }
    }

    @Test
    fun `Theme 6·7 footer 는 1577-0199 를 정신건강위기로 안내한다`() {
        // 1577-0199 는 폐지되지 않았다 — 종전대로 담당 분야 상담을 수행하므로 문구에 남는다.
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).contains("1577-0199 정신건강위기")
        }
    }

    @Test
    fun `자살예방 번호를 생명의전화로 표기하지 않는다`() {
        // 생명의전화는 1588-9191 이다. 과거 이 footer 가 번호와 기관명을 서로 바꿔 표기했다.
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId)).doesNotContain("생명의전화")
        }
    }

    @Test
    fun `R2 보호 문구 자체는 유지된다`() {
        // 안전선(R2) — 완화·제거 금지 (PracticeSafetyFooterCatalog KDoc).
        assertThat(catalog.forTopic(6)).contains("안전이 우선입니다")
        assertThat(catalog.forTopic(7)).contains("안전 확보가 우선입니다")
        for (topicId in listOf<Short>(6, 7)) {
            assertThat(catalog.forTopic(topicId))
                .contains("피해 상황에 있는 분도 이 묵상에 안전하게 머물 수 있게 자원을 함께 안내합니다")
        }
    }
}
