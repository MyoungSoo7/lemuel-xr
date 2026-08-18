package github.lms.lemuel.xr.ai.application

import github.lms.lemuel.xr.ai.application.port.out.LlmCachePort
import github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort
import github.lms.lemuel.xr.ai.application.port.out.LlmMetricsPort
import github.lms.lemuel.xr.ai.domain.LlmCache
import github.lms.lemuel.xr.game.application.SafetyGateFixtures
import github.lms.lemuel.xr.safety.application.ForbiddenTokenScanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * LLM 출력측 안전 게이트.
 *
 * `docs/safety-guidelines.md` §4 의 3단계("백엔드가 LLM 응답을 프론트로 보내기 직전
 * 룰을 한 번 더 적용")를 구현한다. 이 지점을 고른 이유는 묵상·해석 경로와 게임 반응
 * (`ResponseResolver`) 경로가 여기서 합류하기 때문이다 — 한 곳만 막으면 둘 다 막힌다.
 *
 * 정책(enforce): 금지 토큰이 걸리면 1회 재생성 → 재차 걸리면 사전 저작 안전 응답.
 * shadow 로 두지 않는 이유는 이 토큰들이 임베딩 유사도가 아니라 사람이 손으로 쓴
 * 저작 규칙이라 오탐이 거의 없기 때문이다.
 */
class GenerateLlmResponseUseCaseSafetyGateTest {

    private val scanner = ForbiddenTokenScanner(listOf("믿음이 부족", "다시 일어나 싸워"))
    private val safeFallback = "지금은 아무 말도 보태지 않겠습니다. 여기 머무는 것으로 충분합니다."

    // ── 테스트 대역 (mock 이 아니라 동작하는 fake) ─────────────────────────

    private class FakeCache : LlmCachePort {
        val saved = mutableListOf<LlmCache>()
        var preset: LlmCache? = null
        override fun findByCacheKey(cacheKey: String): Optional<LlmCache> = Optional.ofNullable(preset)
        override fun save(cache: LlmCache): LlmCache = cache.also { saved.add(it) }
    }

    private class ScriptedSidecar(private val texts: List<String>) : LlmGenerationPort {
        var calls = 0
        override fun generate(
            purpose: String,
            promptKey: String,
            variables: Map<String, Any?>,
        ): LlmGenerationPort.GenerateResult {
            val text = texts.getOrElse(calls) { texts.last() }
            calls++
            return LlmGenerationPort.GenerateResult(text, "test", "test-model", 1, 1, false)
        }
    }

    private class NoopMetrics : LlmMetricsPort {
        override fun generationDisabled(purpose: String) {}
        override fun cacheHit(purpose: String) {}
        override fun cacheMiss(purpose: String, provider: String?) {}
    }

    private fun useCase(sidecar: LlmGenerationPort, cache: LlmCachePort) =
        GenerateLlmResponseUseCase(
            cache, sidecar, CacheKeyComputer(), NoopMetrics(),
            generationEnabled = true,
            disabledFallback = "",
            forbiddenTokenScanner = scanner,
            forbiddenTokenFallback = safeFallback,
            safetyMetrics = SafetyGateFixtures.metrics(),
        )

    // ── 테스트 ────────────────────────────────────────────────────────────

    @Test
    fun `안전한 응답은 그대로 통과하고 사이드카를 한 번만 부른다`() {
        val sidecar = ScriptedSidecar(listOf("오늘 버텨낸 것만으로 충분합니다."))
        val cache = FakeCache()

        val r = useCase(sidecar, cache).execute("meditation", "p", emptyMap())

        assertThat(r.text).isEqualTo("오늘 버텨낸 것만으로 충분합니다.")
        assertThat(sidecar.calls).isEqualTo(1)
    }

    @Test
    fun `금지 토큰이 걸리면 한 번 재생성한다`() {
        val sidecar = ScriptedSidecar(
            listOf("믿음이 부족해서 그렇습니다.", "그 자리에 함께 있겠습니다."),
        )
        val cache = FakeCache()

        val r = useCase(sidecar, cache).execute("meditation", "p", emptyMap())

        assertThat(sidecar.calls).isEqualTo(2)
        assertThat(r.text).isEqualTo("그 자리에 함께 있겠습니다.")
    }

    @Test
    fun `재생성도 걸리면 사전 저작 안전 응답을 반환한다`() {
        val sidecar = ScriptedSidecar(
            listOf("믿음이 부족합니다.", "다시 일어나 싸워야죠."),
        )
        val cache = FakeCache()

        val r = useCase(sidecar, cache).execute("meditation", "p", emptyMap())

        assertThat(sidecar.calls).isEqualTo(2)
        assertThat(r.text).isEqualTo(safeFallback)
    }

    @Test
    fun `차단된 응답은 캐시에 저장하지 않는다`() {
        val sidecar = ScriptedSidecar(
            listOf("믿음이 부족합니다.", "다시 일어나 싸워야죠."),
        )
        val cache = FakeCache()

        useCase(sidecar, cache).execute("meditation", "p", emptyMap())

        // 차단된 문장이 캐시에 들어가면 다음 요청은 캐시 hit 로 게이트를 우회한다.
        assertThat(cache.saved.map { it.response })
            .noneMatch { it != null && it.contains("믿음이 부족") }
            .noneMatch { it != null && it.contains("다시 일어나 싸워") }
    }

    @Test
    fun `게이트 이전에 캐시된 오염 응답도 차단한다`() {
        val sidecar = ScriptedSidecar(listOf("안 불려야 한다"))
        val cache = FakeCache()
        cache.preset = LlmCache.freshEntry(
            "k", "믿음이 부족해서입니다.", "test", "test-model", "meditation",
            1, 1, java.time.LocalDateTime.now(),
        )

        val r = useCase(sidecar, cache).execute("meditation", "p", emptyMap())

        assertThat(r.text).isEqualTo(safeFallback)
    }
}
