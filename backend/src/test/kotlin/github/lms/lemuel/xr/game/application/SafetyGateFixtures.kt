package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.safety.adapter.out.metrics.MicrometerSafetyMetricsAdapter
import github.lms.lemuel.xr.safety.application.ForbiddenTokenSanitizer
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import github.lms.lemuel.xr.safety.application.port.out.SafetyMetricsPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

/**
 * 금지 토큰 게이트 테스트 픽스처.
 *
 * 게이트가 여러 출구(`responseText` · `scenePayload` · `valuePrompt`)에 걸려 있어서
 * 협력자 조립이 여러 테스트에 반복된다. 대체 문구를 각 테스트가 따로 들고 있으면
 * "두 경로가 같은 문구를 낸다" 는 단언이 사실은 각자 다른 상수를 비교하는 일이 될 수 있다.
 * 하나의 출처를 둔다.
 *
 * **이 상수들은 손으로 옮겨 적은 값이라 yml 이 바뀌어도 저절로 따라오지 않는다.** 그런데도
 * 이 파일을 쓰는 테스트들은 계속 초록이다 — 자기가 만든 sanitizer 를 자기가 만든 상수와
 * 비교하니 낡은 값끼리도 언제나 일관되기 때문이다. 그래서 실제 설정과의 대조는
 * `ForbiddenTokenConfigTest.단위 테스트 픽스처가 실제 설정과 어긋나지 않는다` 가 맡는다
 * (스프링 컨텍스트를 띄우는 쪽). 여기 값을 고칠 일이 생기면 그 테스트가 먼저 깨진다.
 */
internal object SafetyGateFixtures {

    /** `application.yml` 의 `safety.forbidden-tokens.list` 에서 뽑은 실제 토큰 일부. */
    val TOKENS = listOf("믿음이 부족", "빨리 회복")

    /** `application.yml` 의 `safety.forbidden-tokens.fallback-text` 실값. */
    const val FALLBACK_TEXT = "지금은 어떤 말도 보태지 않겠습니다. 여기 이대로 머물러도 괜찮습니다."

    /**
     * 목이 아니라 *진짜 어댑터* 를 준다 — 레지스트리만 인메모리다.
     * 목을 쓰면 "포트를 불렀다" 까지만 재고, 태그 이름·값이 틀려도 초록이다.
     */
    fun metrics(): SafetyMetricsPort = MicrometerSafetyMetricsAdapter(SimpleMeterRegistry())

    fun sanitizer(metrics: SafetyMetricsPort = metrics()): ForbiddenTokenSanitizer =
        ForbiddenTokenSanitizer(ForbiddenTokenScanner(TOKENS), metrics, FALLBACK_TEXT)
}
