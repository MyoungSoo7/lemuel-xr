package github.lms.lemuel.xr.ai.application

import github.lms.lemuel.xr.ai.application.port.out.LlmCachePort
import github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort
import github.lms.lemuel.xr.ai.application.port.out.LlmMetricsPort
import github.lms.lemuel.xr.ai.domain.LlmCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

/**
 * GenerateLlmResponseUseCase 단위 테스트 — 캐시 hit / miss / 생성 비활성화 분기를 Mockito 로 커버.
 * 실제 사이드카/DB 호출 없음. 진짜 [CacheKeyComputer] + mock [LlmMetricsPort] 사용.
 * 포트는 도메인 [LlmCache] 로만 대화하고 JPA 엔티티는 등장하지 않는다.
 */
class GenerateLlmResponseUseCaseTest {

    private val keyer = CacheKeyComputer()
    private val metrics: LlmMetricsPort = mock()

    private fun uc(
        cache: LlmCachePort,
        sidecar: LlmGenerationPort,
        enabled: Boolean,
        fallback: String,
    ): GenerateLlmResponseUseCase =
        GenerateLlmResponseUseCase(cache, sidecar, keyer, metrics, enabled, fallback)

    private fun cached(key: String, response: String): LlmCache =
        LlmCache(
            key, response, "anthropic", "claude-x", "meditation",
            null, null, 3, null, LocalDateTime.now(), null,
        )

    // ───────────────────────── 생성 비활성화 (enabled=false) ─────────────────────────

    @Test
    fun `비활성화 사전캐시 있으면 캐시응답 반환`() {
        val cache: LlmCachePort = mock()
        val sidecar: LlmGenerationPort = mock()
        val key = keyer.compute("k", mapOf("a" to 1))
        whenever(cache.findByCacheKey(key)).thenReturn(Optional.of(cached(key, "사전캐시 응답")))

        val r = uc(cache, sidecar, false, "정적 fallback").execute("meditation", "k", mapOf("a" to 1))

        assertThat(r.text).isEqualTo("사전캐시 응답")
        assertThat(r.cached).isTrue()
        assertThat(r.provider).isEqualTo("anthropic")
        verify(sidecar, never()).generate(any(), any(), any())
        verify(metrics).generationDisabled("meditation")
    }

    @Test
    fun `비활성화 사전캐시 없으면 정적 fallback`() {
        val cache: LlmCachePort = mock()
        val sidecar: LlmGenerationPort = mock()
        whenever(cache.findByCacheKey(any())).thenReturn(Optional.empty())

        val r = uc(cache, sidecar, false, "정적 fallback 텍스트").execute("meditation", "k", emptyMap())

        assertThat(r.text).isEqualTo("정적 fallback 텍스트")
        assertThat(r.provider).isEqualTo("static")
        assertThat(r.model).isEqualTo("fallback")
        assertThat(r.cached).isFalse()
        verify(sidecar, never()).generate(any(), any(), any())
        verify(metrics).generationDisabled("meditation")
    }

    // ───────────────────────── 생성 활성화 (enabled=true) ─────────────────────────

    @Test
    fun `활성화 캐시 hit 이면 hitCount 증가 사이드카 미호출`() {
        val cache: LlmCachePort = mock()
        val sidecar: LlmGenerationPort = mock()
        val key = keyer.compute("k", mapOf("x" to "y"))
        whenever(cache.findByCacheKey(key)).thenReturn(Optional.of(cached(key, "캐시된 본문")))

        val r = uc(cache, sidecar, true, "").execute("meditation", "k", mapOf("x" to "y"))

        assertThat(r.text).isEqualTo("캐시된 본문")
        assertThat(r.cached).isTrue()
        verify(sidecar, never()).generate(any(), any(), any())
        verify(metrics).cacheHit("meditation")

        // hit 경로는 hitCount 증가(3→4) + lastHitAt 설정된 복사본을 저장한다.
        val saved = argumentCaptor<LlmCache>()
        verify(cache).save(saved.capture())
        assertThat(saved.firstValue.hitCount).isEqualTo(4)
        assertThat(saved.firstValue.lastHitAt).isNotNull()
    }

    @Test
    fun `활성화 캐시 miss 이면 사이드카 호출 후 저장`() {
        val cache: LlmCachePort = mock()
        val sidecar: LlmGenerationPort = mock()
        whenever(cache.findByCacheKey(any())).thenReturn(Optional.empty())
        whenever(sidecar.generate(any(), any(), any())).thenReturn(
            LlmGenerationPort.GenerateResult("새 본문", "anthropic", "claude-x", 10, 20, false),
        )

        val r = uc(cache, sidecar, true, "").execute("meditation", "k", mapOf("a" to 1))

        assertThat(r.text).isEqualTo("새 본문")
        assertThat(r.cached).isFalse()
        assertThat(r.provider).isEqualTo("anthropic")

        val saved = argumentCaptor<LlmCache>()
        verify(cache).save(saved.capture())
        val e = saved.firstValue
        assertThat(e.response).isEqualTo("새 본문")
        assertThat(e.provider).isEqualTo("anthropic")
        assertThat(e.purpose).isEqualTo("meditation")
        assertThat(e.promptTokens).isEqualTo(10)
        assertThat(e.completionTokens).isEqualTo(20)
        assertThat(e.hitCount).isZero()
        assertThat(e.createdAt).isNotNull()
        verify(metrics).cacheMiss("meditation", "anthropic")
    }

    @Test
    fun `활성화 miss provider null 이면 메트릭 태그 unknown`() {
        val cache: LlmCachePort = mock()
        val sidecar: LlmGenerationPort = mock()
        whenever(cache.findByCacheKey(any())).thenReturn(Optional.empty())
        whenever(sidecar.generate(any(), any(), any())).thenReturn(
            LlmGenerationPort.GenerateResult("본문", null, null, null, null, false),
        )

        val r = uc(cache, sidecar, true, "").execute("scene", "k", emptyMap())

        assertThat(r.provider).isNull()
        // provider null → 어댑터가 "unknown" 태그로 변환. 유즈케이스는 raw null 을 그대로 전달.
        verify(metrics).cacheMiss(eq("scene"), eq(null))
    }
}
