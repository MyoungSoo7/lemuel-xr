package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.safety.application.ForbiddenTokenSanitizer
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner

/**
 * 금지 토큰 게이트 테스트 픽스처.
 *
 * 게이트가 여러 출구(`responseText` · `scenePayload` · `valuePrompt`)에 걸려 있어서
 * 협력자 조립이 여러 테스트에 반복된다. 대체 문구를 각 테스트가 따로 들고 있으면
 * "두 경로가 같은 문구를 낸다" 는 단언이 사실은 각자 다른 상수를 비교하는 일이 될 수 있다.
 * 하나의 출처를 둔다.
 */
internal object SafetyGateFixtures {

    /** `application.yml` 의 `safety.forbidden-tokens.list` 에서 뽑은 실제 토큰 일부. */
    val TOKENS = listOf("믿음이 부족", "빨리 회복")

    /** `application.yml` 의 `safety.forbidden-tokens.fallback-text` 실값. */
    const val FALLBACK_TEXT = "지금은 어떤 말도 보태지 않겠습니다. 여기 이대로 머물러도 괜찮습니다."

    fun sanitizer(): ForbiddenTokenSanitizer =
        ForbiddenTokenSanitizer(ForbiddenTokenScanner(TOKENS), FALLBACK_TEXT)
}
