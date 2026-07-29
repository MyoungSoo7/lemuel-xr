package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 저작 YAML(`content/{인물}/scene*.yml`)의 `lint_forbidden_tokens` 가 실제로
 * 런타임 설정에 반영돼 있는지 검증한다.
 *
 * 이 테스트의 존재 이유: 이 목록은 오랫동안 저작 파일에만 있었고 어떤 코드도 읽지 않았다.
 * "설정에 적혀 있다"와 "빈으로 주입돼 동작한다" 사이가 벌어지면 게이트는 다시 주석이 된다.
 * content/ 는 백엔드 클래스패스가 아니므로 자동 동기화가 안 된다 — 그래서 대표 토큰이
 * 빠지면 여기서 깨지게 해 둔다.
 */
class ForbiddenTokenConfigTest : IntegrationTestBase() {

    @Autowired
    private lateinit var scanner: ForbiddenTokenScanner

    @Test
    fun `스캐너가 스프링 빈으로 등록돼 있다`() {
        assertThat(scanner).isNotNull()
    }

    @Test
    fun `저작 규칙의 대표 가스라이팅 토큰이 런타임에 살아 있다`() {
        // 각 인물 시나리오의 핵심 금지 표현. 하나라도 빠지면 그 시나리오의
        // 안전 규칙이 런타임에서 무력화된 것이다.
        val mustBlock = listOf(
            "믿음이 부족해서 그렇습니다",       // 공통 — 책임 전가
            "빨리 회복하셔야죠",                // 공통 — 회복 압박
            "다시 일어나 싸워야 합니다",         // 엘리야 — 탈진 상태에 투쟁 요구
            "정신 차려요",                      // 엘리야 — 질책
            "허무를 극복하세요",                // 솔로몬 — 실존적 공허를 결함 취급
            "욕심이 많아서 그래요",             // 솔로몬 — 비난
            "사역으로 돌아가세요",              // 엘리야 — 역할 복귀 압박
        )

        val leaked = mustBlock.filterNot { scanner.scan(it).matched }

        assertThat(leaked)
            .describedAs("이 문장들이 게이트를 통과했다 — 금지 토큰 설정이 누락됐다")
            .isEmpty()
    }

    @Test
    fun `정상적인 위로 문장은 차단하지 않는다`() {
        // 과차단(false positive)은 콘텐츠를 망가뜨린다. 게이트가 지나치게 넓지 않은지 확인.
        val mustPass = listOf(
            "오늘 버텨낸 것만으로 충분합니다.",
            "하나님은 책망보다 떡과 잠을 먼저 주셨습니다.",
            "지금 쉬어도 괜찮습니다.",
            "당신의 슬픔에는 이유가 있습니다.",
        )

        val blocked = mustPass.filter { scanner.scan(it).matched }

        assertThat(blocked)
            .describedAs("정상 문장이 차단됐다 — 토큰이 지나치게 넓다")
            .isEmpty()
    }
}
