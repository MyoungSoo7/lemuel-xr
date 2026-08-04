package github.lms.lemuel.xr.config

import github.lms.lemuel.xr.IntegrationTestBase
import github.lms.lemuel.xr.ai.grounding.application.GoldenSetEvaluationJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

/**
 * **켠 상태의 컨텍스트가 뜨는지** 를 검사한다. 이 파일이 존재하는 이유는 사고다.
 *
 * [GroundingEvalConfig] 는 `grounding.eval.enabled=false` 가 기본이라, 여기 있는 빈들은
 * 이 저장소의 어떤 테스트에서도 *생성된 적이 없었다*. 배선이 틀렸어도 초록불이 나온다는 뜻이다.
 * 그리고 실제로 틀려 있었다 — 2026-08-05 운영에서 처음 켜자마자:
 *
 * ```
 * Cannot subclass final class ...GoldenSetEvaluationJob  → CrashLoopBackOff
 * ```
 *
 * ShedLock 의 기본 interceptMode(PROXY_METHOD)가 `@SchedulerLock` 빈을 CGLIB 로 상속하는데
 * Kotlin 클래스는 기본 final 이고, `kotlin("plugin.spring")` 이 열어 주는 건 `@Component`
 * 계열뿐이다. `@Bean` 으로 등록한 이 잡은 그 자동화의 사각지대였다.
 *
 * 교훈은 "open 을 빠뜨렸다" 가 아니라 **기본 비활성 기능은 기본 비활성인 채로 검증된다**
 * 는 것이다. 켜는 것이 배포자의 선택이라면, 켠 구성 하나는 테스트가 갖고 있어야 한다.
 *
 * apiKey 는 더미다. 부팅 시엔 어댑터 생성만 하고 호출하지 않으며, 채점은 03:30 크론이라
 * 테스트 중 발화하지 않는다 — 즉 이 테스트는 유료 API 를 한 번도 부르지 않는다.
 */
@TestPropertySource(
    properties = [
        "grounding.eval.enabled=true",
        "grounding.eval.api-key=test-dummy-key-not-used",
    ],
)
class GroundingEvalContextTest : IntegrationTestBase() {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `채점을 켜면 컨텍스트가 뜨고 잡이 프록시로 감싸진다`() {
        val job = context.getBean(GoldenSetEvaluationJob::class.java)

        assertThat(AopUtils.isCglibProxy(job))
            .describedAs(
                "GoldenSetEvaluationJob 이 프록시가 아니다. ShedLock 의 분산 락이 실제로는 " +
                    "걸리지 않는다는 뜻 — 롤링 업데이트로 파드가 공존하는 순간 양쪽이 동시에 " +
                    "유료 임베딩 API 를 태운다. 클래스와 run() 의 `open` 을 확인할 것.",
            )
            .isTrue()
    }

    @Test
    fun `켜진 구성의 빈이 모두 배선된다`() {
        assertThat(context.getBeansOfType(GoldenSetEvaluationJob::class.java)).hasSize(1)
        assertThat(context.containsBean("goldenSetEmbeddingPort")).isTrue()
        assertThat(context.containsBean("evaluateGoldenSetUseCase")).isTrue()
        assertThat(context.containsBean("goldenSetEvalMetricsPort")).isTrue()
    }
}
