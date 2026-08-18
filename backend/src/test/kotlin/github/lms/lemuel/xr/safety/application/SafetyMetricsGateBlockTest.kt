package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.LemuelXrApplication
import github.lms.lemuel.xr.ai.application.CacheKeyComputer
import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.ai.application.port.out.LlmCachePort
import github.lms.lemuel.xr.ai.application.port.out.LlmGenerationPort
import github.lms.lemuel.xr.ai.application.port.out.LlmMetricsPort
import github.lms.lemuel.xr.ai.domain.LlmCache
import github.lms.lemuel.xr.safety.adapter.out.metrics.MicrometerSafetyMetricsAdapter
import github.lms.lemuel.xr.safety.application.port.out.SafetyMetricsPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

/**
 * 금지 토큰 게이트의 **계량**(`content.gate.block`) — seed AC3.
 *
 * 왜 필요한가. 게이트는 두 층에 있는데 신호는 한쪽에만 있었다. 저작 문구 층
 * ([ForbiddenTokenSanitizer])은 `log.warn` 이라도 남겼지만, LLM 층
 * ([GenerateLlmResponseUseCase])은 **로그도 메트릭도 없이** 대체 문구를 반환했다.
 * 그래서 운영자가 알 수 없는 것이 셋이었다 — (a) 게이트가 살아 있는지, (b) 모델이
 * 금칙 문장을 얼마나 자주 만드는지, (c) 그게 정신건강 축인지 신학 축인지.
 *
 * 이 스위트가 고정하는 것:
 * 1. **축 지도가 저작과 어긋나면 빨강** — 축의 1차 출처는 저작 yml 의 `T1_*` 게이트 id 이고
 *    `application.yml` 의 `theology-list` 는 그 사본이다. 사본이 낡으면 신학 차단이 조용히
 *    정신건강 축으로 계량된다. 이 클래스에서 **가장 중요한 단언**이다.
 * 2. 두 층 모두 계량한다 — 층 태그(`llm`/`content`)로 구분된다.
 * 3. 메트릭에 **금칙 자구가 실리지 않는다** — 그 문구들은 가스라이팅 표현 그 자체다.
 * 4. 부팅 시 축×층 격자가 0 으로 선다 — 「아직 안 걸림」과 「게이트가 없음」을 가른다.
 *
 * 스프링 컨텍스트를 띄우지 않는다. 대신 `application.yml` 을 [ContentSafetyGateEnforcementTest]
 * 와 **같은 방식으로** 직접 읽는다 — 그 파일이 런타임 값의 단일 출처이므로 같은 것을 재고,
 * 대신 스위트가 초 단위로 돈다.
 */
class SafetyMetricsGateBlockTest {

    // ── 1. 축 지도 드리프트 (이 스위트의 핵심) ──────────────────────────────

    @Test
    fun `설정의 theology-list 는 저작 yml 의 T1 게이트 토큰과 정확히 일치한다`() {
        val authored = authoredTheologyTokens()
        val configured = configuredTheologyTokens().toSet()

        // 저작이 0건이면 이 단언은 "빈 집합 == 빈 집합" 으로 언제나 초록이다 — 그 공허한
        // 초록을 막는다. T1 축은 rahab 저작과 함께 실재하므로 0 은 목록 유실의 신호다.
        assertThat(authored)
            .describedAs("저작 yml 에 T1_* 게이트가 하나도 없다 — 축 지도가 아니라 저작이 사라진 것이다")
            .isNotEmpty()

        assertThat(configured).describedAs(
            "safety.forbidden-tokens.theology-list 가 저작 T1 게이트와 어긋난다.\n" +
                "  저작에만: ${(authored - configured).sorted()}\n" +
                "  설정에만: ${(configured - authored).sorted()}\n" +
                "축의 1차 출처는 저작 yml 의 게이트 id 다. 저작을 고쳤으면 설정도 따라와야 한다 — " +
                "어긋난 채로 두면 신학 차단이 content.gate.block{axis=R2_R3} 으로 계량돼 " +
                "운영자가 잘못된 대응(문구 조정)을 하게 된다.",
        ).isEqualTo(authored)
    }

    @Test
    fun `theology-list 의 모든 토큰은 실제로 스캔되는 list 에 들어 있다`() {
        val runtime = runtimeTokens().toSet()
        val orphans = configuredTheologyTokens().filterNot { it in runtime }

        // 축만 선언하고 목록에 없으면 그 토큰은 *한 번도 걸리지 않는다*. 축 지도가 커 보여도
        // 집행되는 건 0 종인 상태 — 이 저장소가 반복해서 잡아 온 "선언만 있는 게이트" 형태다.
        assertThat(orphans).describedAs(
            "theology-list 에만 있고 safety.forbidden-tokens.list 에 없는 토큰 — 영원히 안 걸린다",
        ).isEmpty()
    }

    // ── 2. 축·층 계량 ──────────────────────────────────────────────────────

    @Test
    fun `저작 문구 층에서 신학 토큰이 걸리면 axis=T1 layer=content 로 계량된다`() {
        val registry = SimpleMeterRegistry()
        val token = configuredTheologyTokens().first()
        val sanitizer = sanitizer(registry, tokens = listOf(token), theology = listOf(token))

        sanitizer.sanitizeText("그 사람은 ${token} 새 인생을 얻었어요", "t")

        assertThat(count(registry, axis = "T1", layer = "content")).isEqualTo(1.0)
    }

    @Test
    fun `축 예외에 없는 토큰은 axis=R2_R3 으로 계량된다`() {
        val registry = SimpleMeterRegistry()
        val sanitizer = sanitizer(registry, tokens = listOf("믿음이 부족"), theology = emptyList())

        sanitizer.sanitizeText("믿음이 부족해서 그런 겁니다", "t")

        assertThat(count(registry, axis = "R2_R3", layer = "content")).isEqualTo(1.0)
        // 격자를 세우지 않은 레지스트리라 T1 계열은 *아예 없다*(NaN). 0 이 아니라 부재여야
        // 한다 — 여기서 0 이 나온다면 R2_R3 차단이 T1 계열도 함께 만들었다는 뜻이다.
        assertThat(count(registry, axis = "T1", layer = "content")).isNaN()
    }

    @Test
    fun `LLM 층은 재시도로 풀린 차단까지 센다`() {
        val registry = SimpleMeterRegistry()
        // 1차는 걸리고 2차는 깨끗하다 — 사용자는 대체 문구를 보지 않지만 게이트는 한 번 막았다.
        val sidecar = ScriptedSidecar(listOf("믿음이 부족해서요", "오늘 버텨낸 것만으로 충분합니다."))

        val result = useCase(registry, sidecar, FakeCache()).execute("meditation", "p", emptyMap())

        assertThat(result.text).isEqualTo("오늘 버텨낸 것만으로 충분합니다.")
        assertThat(count(registry, axis = "R2_R3", layer = "llm"))
            .describedAs("재시도로 풀린 차단을 안 세면 '모델이 계속 금칙 문장을 만드는데 재시도가 가려 주는' 상태가 지표에서 사라진다")
            .isEqualTo(1.0)
    }

    @Test
    fun `LLM 층은 두 번 연속 차단도 센다`() {
        val registry = SimpleMeterRegistry()
        val sidecar = ScriptedSidecar(listOf("믿음이 부족해서요", "믿음이 부족한 탓입니다"))

        val result = useCase(registry, sidecar, FakeCache()).execute("meditation", "p", emptyMap())

        assertThat(result.model).isEqualTo("safety-fallback")
        assertThat(count(registry, axis = "R2_R3", layer = "llm")).isEqualTo(2.0)
    }

    @Test
    fun `게이트 도입 전 캐시된 오염 응답도 LLM 층으로 계량된다`() {
        val registry = SimpleMeterRegistry()
        val cache = FakeCache().apply { preset = cached("믿음이 부족했던 겁니다") }

        val result = useCase(registry, ScriptedSidecar(listOf("안 불린다")), cache)
            .execute("meditation", "p", emptyMap())

        assertThat(result.model).isEqualTo("safety-fallback")
        assertThat(count(registry, axis = "R2_R3", layer = "llm")).isEqualTo(1.0)
    }

    // ── 3. 자구 유출 ───────────────────────────────────────────────────────

    @Test
    fun `rule_id 는 금칙 자구를 담지 않는다`() {
        val registry = SimpleMeterRegistry()
        sanitizer(registry, tokens = listOf("믿음이 부족"), theology = emptyList())
            .sanitizeText("믿음이 부족해서 그런 겁니다", "t")

        val ruleIds = registry.find("content.gate.block").counters()
            .filter { it.count() > 0.0 }
            .mapNotNull { it.id.getTag("rule_id") }

        assertThat(ruleIds).hasSize(1)
        // 대시보드·시계열 저장소로 나가는 유일한 규칙 식별자다. 자구가 실리면 금칙 문구가
        // 그대로 밖으로 나간다 — 그 문구들은 가스라이팅 표현 자체라 노출 자체가 해다.
        assertThat(ruleIds.single()).matches("[0-9a-f]{12}")
        assertThat(ruleIds.single()).doesNotContain("믿음")
    }

    @Test
    fun `rule_id 는 토큰마다 안정적이고 서로 다르다`() {
        val a = ForbiddenTokenScanner.ruleIdOf("믿음이 부족")
        val b = ForbiddenTokenScanner.ruleIdOf("빨리 회복")

        assertThat(a).isEqualTo(ForbiddenTokenScanner.ruleIdOf("믿음이 부족"))
        assertThat(a).isNotEqualTo(b)
    }

    // ── 4. 부팅 시 격자 ────────────────────────────────────────────────────

    @Test
    fun `부팅 시 축 곱하기 층 격자가 0 으로 등록된다`() {
        val registry = SimpleMeterRegistry()
        MicrometerSafetyMetricsAdapter(registry).preRegisterGrid()

        for (axis in listOf("R2_R3", "T1")) {
            for (layer in listOf("llm", "content")) {
                assertThat(count(registry, axis, layer)).describedAs(
                    "axis=$axis layer=$layer 계열이 없다 — 대시보드에서 '아직 안 걸림' 과 " +
                        "'게이트가 배포되지 않음' 이 똑같이 보이게 된다",
                ).isEqualTo(0.0)
            }
        }
    }

    // ── 조립 ───────────────────────────────────────────────────────────────

    private fun sanitizer(
        registry: MeterRegistry,
        tokens: List<String>,
        theology: List<String>,
    ) = ForbiddenTokenSanitizer(
        ForbiddenTokenScanner(tokens, theology),
        MicrometerSafetyMetricsAdapter(registry),
        "지금은 어떤 말도 보태지 않겠습니다.",
    )

    private fun useCase(registry: MeterRegistry, sidecar: LlmGenerationPort, cache: LlmCachePort) =
        GenerateLlmResponseUseCase(
            cache, sidecar, CacheKeyComputer(), NoopLlmMetrics(),
            generationEnabled = true,
            disabledFallback = "",
            forbiddenTokenScanner = ForbiddenTokenScanner(listOf("믿음이 부족")),
            forbiddenTokenFallback = "지금은 어떤 말도 보태지 않겠습니다.",
            safetyMetrics = MicrometerSafetyMetricsAdapter(registry),
        )

    /**
     * 그 축×층 계열의 합. 계열이 아예 없으면 [Double.NaN] 을 돌려 「0」과 구분한다 —
     * 이 스위트 자신이 "없는 것" 을 "0" 으로 읽으면 격자 테스트가 공허해진다.
     */
    private fun count(registry: MeterRegistry, axis: String, layer: String): Double {
        val counters = registry.find("content.gate.block")
            .tag("axis", axis).tag("layer", layer).counters()
        if (counters.isEmpty()) return Double.NaN
        return counters.sumOf { it.count() }
    }

    // ── 테스트 대역 ────────────────────────────────────────────────────────

    private class FakeCache : LlmCachePort {
        var preset: LlmCache? = null
        override fun findByCacheKey(cacheKey: String): Optional<LlmCache> = Optional.ofNullable(preset)
        override fun save(cache: LlmCache): LlmCache = cache
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

    private class NoopLlmMetrics : LlmMetricsPort {
        override fun generationDisabled(purpose: String) {}
        override fun cacheHit(purpose: String) {}
        override fun cacheMiss(purpose: String, provider: String?) {}
    }

    // ── 저작·설정 읽기 ─────────────────────────────────────────────────────

    /** 저작 yml 의 `T1_*` 게이트가 선언한 금지 토큰 전량. 축의 **1차 출처**다. */
    private fun authoredTheologyTokens(): Set<String> =
        sceneFiles().flatMap { f ->
            gatesOf(f)
                .filter { THEOLOGY_AXIS_ID.containsMatchIn(it["id"]?.toString().orEmpty()) }
                .flatMap { g -> (g["lint_forbidden_tokens"] as? List<Any?>).orEmpty() }
                .map { it.toString().trim() }
                .filter { it.isNotEmpty() }
        }.toSet()

    private fun configuredTheologyTokens(): List<String> = forbiddenTokensNode("theology-list")

    private fun runtimeTokens(): List<String> = forbiddenTokensNode("list")

    /** Spring 이 `List<String>` 으로 바인딩할 때와 같은 규칙 — 콤마 분리 + trim. */
    private fun forbiddenTokensNode(key: String): List<String> {
        val app = moduleRoot().resolve("src/main/resources/application.yml").toFile()
        @Suppress("UNCHECKED_CAST")
        val root = app.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val safety = root["safety"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val forbidden = safety["forbidden-tokens"] as Map<String, Any?>
        val raw = checkNotNull(forbidden[key]) { "safety.forbidden-tokens.$key 가 없다" }
        return raw.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun gatesOf(file: File): List<Map<String, Any?>> {
        val root = file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as? Map<String, Any?>
            ?: return emptyList()
        return (root["safety_gates"] as? List<Map<String, Any?>>).orEmpty()
    }

    /** [ContentSafetyGateEnforcementTest.sceneFiles] 와 같은 범위여야 한다 — 트랙 B + Stage 1. */
    private fun sceneFiles(): List<File> {
        val trackB = repoRoot().resolve("content").toFile()
        check(trackB.isDirectory) { "저작 콘텐츠 디렉터리 없음: $trackB" }
        val stage1 = moduleRoot().resolve("src/main/resources/scenarios").toFile()
        check(stage1.isDirectory) { "Stage 1 시나리오 디렉터리 없음: $stage1" }

        val b = trackB.walkTopDown()
            .filter { it.isFile && it.name.startsWith("scene") && it.name.endsWith(".yml") }
        val a = stage1.walkTopDown().filter { it.isFile && it.name.endsWith(".yml") }
        return (b + a).sortedBy { it.path }.toList()
    }

    private fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    private fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }

    private fun cached(response: String): LlmCache =
        LlmCache(
            "k", response, "test", "test-model", "meditation",
            null, null, 3, null, java.time.LocalDateTime.now(), null,
        )

    private companion object {
        /**
         * 신학 축 게이트 id. `ContentSafetyGateEnforcementTest.AXIS_PREFIX` 의 T1 갈래와
         * 같은 정의여야 한다 — 한쪽만 넓히면 반대쪽이 조용히 통과시킨다.
         */
        val THEOLOGY_AXIS_ID = Regex("""^T1(_|$)""")
    }
}
