# Scripture-Grounding Shadow Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, shadow-mode gate that scores whether each sentence of a generated meditation is grounded in the supplied scripture passages, emitting a verdict + metrics without changing user-facing behavior.

**Architecture:** New `github.lms.lemuel.xr.ai.grounding` sub-package split by hexagonal layer (domain → application → adapter). Sentence-level embedding grounding: split text → embed sentences and passages → per-sentence max cosine vs passages → aggregate to an accept/reject verdict via a policy. Embedding lives behind `EmbeddingPort` (fake in unit tests, real Gemini adapter in a tagged validation harness).

**Tech Stack:** Kotlin, Spring Boot 4, JUnit 5 (`useJUnitPlatform`), AssertJ, mockito-kotlin, Micrometer, Spring `RestClient`. Build/test via system `gradle` (no wrapper) from `backend/`.

## Global Constraints

- Package root: `github.lms.lemuel.xr.ai.grounding` — all new code under `backend/src/main/kotlin/.../ai/grounding/`, tests mirror under `backend/src/test/kotlin/.../ai/grounding/`.
- Hexagonal: application depends only on domain + out-ports; **no JPA, no Spring web types, no `ScripturePassage` import** in domain/application. Evidence enters as the local `EvaluateGroundingUseCase.Passage(reference, text)` value; a caller maps `ScripturePassage → Passage` (mapping is out of this plan's scope).
- Shadow mode: this code is **never** wired into the live `GenerateLlmResponseUseCase` path in this plan, and does **not** re-enable generation.
- Test command (run from `backend/`): `gradle test --tests "<FQCN>"`.
- KDoc comments in Korean, matching existing files (e.g. `GenerateLlmResponseUseCase`).
- The live validation harness must self-disable without an API key via `@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")` and carry `@Tag("live-embedding")`.
- All commits on branch `feat/scripture-grounding-shadow-gate`. Commit only files this plan creates; never stage the repo's pre-existing uncommitted WIP (scenario `*.yml`, frontend files).

---

## File Structure

**Main:**
- `ai/grounding/domain/CosineSimilarity.kt` — pure cosine function.
- `ai/grounding/domain/GroundingModels.kt` — `GroundingStatus`, `SentenceGrounding`, `GroundingVerdict`, `GroundingPolicy`.
- `ai/grounding/application/SentenceSplitter.kt` — Korean-aware sentence split.
- `ai/grounding/application/port/out/EmbeddingPort.kt`
- `ai/grounding/application/port/out/GroundingMetricsPort.kt`
- `ai/grounding/application/EvaluateGroundingUseCase.kt` — orchestrator.
- `ai/grounding/adapter/out/metrics/MicrometerGroundingMetricsAdapter.kt`
- `ai/grounding/adapter/out/embedding/GeminiEmbeddingAdapter.kt` — real embedding (validation only).

**Test:**
- `ai/grounding/domain/CosineSimilarityTest.kt`
- `ai/grounding/domain/GroundingModelsTest.kt`
- `ai/grounding/application/SentenceSplitterTest.kt`
- `ai/grounding/application/FakeEmbeddingAdapter.kt` — deterministic test double.
- `ai/grounding/application/EvaluateGroundingUseCaseTest.kt`
- `ai/grounding/adapter/out/metrics/MicrometerGroundingMetricsAdapterTest.kt`
- `ai/grounding/validation/ScriptureGroundingValidationTest.kt` — tagged live harness.
- `backend/src/test/resources/grounding/*.json` — 5 fixtures.

---

## Task 1: Cosine similarity (domain)

**Files:**
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/domain/CosineSimilarity.kt`
- Test: `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/domain/CosineSimilarityTest.kt`

**Interfaces:**
- Produces: `object CosineSimilarity { fun cosine(a: FloatArray, b: FloatArray): Double }` — returns `0.0` if either vector has zero magnitude; throws `IllegalArgumentException` on length mismatch.

- [ ] **Step 1: Write the failing test**

```kotlin
package github.lms.lemuel.xr.ai.grounding.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CosineSimilarityTest {

    @Test
    fun `identical vectors have cosine 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertThat(CosineSimilarity.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `orthogonal vectors have cosine 0`() {
        assertThat(CosineSimilarity.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
            .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `opposite vectors have cosine -1`() {
        assertThat(CosineSimilarity.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)))
            .isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `zero magnitude vector yields 0 not NaN`() {
        assertThat(CosineSimilarity.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f))).isEqualTo(0.0)
    }

    @Test
    fun `length mismatch throws`() {
        assertThatThrownBy { CosineSimilarity.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.domain.CosineSimilarityTest"`
Expected: FAIL — `CosineSimilarity` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package github.lms.lemuel.xr.ai.grounding.domain

import kotlin.math.sqrt

/**
 * 두 임베딩 벡터의 코사인 유사도 — 순수 함수. 프레임워크 무관.
 * 크기 0 벡터는 NaN 대신 0.0 을 반환한다(근거 없음으로 취급).
 */
object CosineSimilarity {
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "벡터 길이 불일치: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            na += a[i].toDouble() * a[i]
            nb += b[i].toDouble() * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.domain.CosineSimilarityTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/domain/CosineSimilarity.kt \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/domain/CosineSimilarityTest.kt
git commit -m "feat(grounding): 코사인 유사도 순수 함수 추가"
```

---

## Task 2: Domain value objects + policy

**Files:**
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/domain/GroundingModels.kt`
- Test: `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/domain/GroundingModelsTest.kt`

**Interfaces:**
- Produces:
  - `enum class GroundingStatus { ACCEPTED, REJECTED, INCONCLUSIVE, NO_EVIDENCE }`
  - `data class SentenceGrounding(sentence: String, maxSimilarity: Double, grounded: Boolean, bestPassageRef: String?)`
  - `data class GroundingVerdict(status: GroundingStatus, unsupportedRate: Double, sentenceResults: List<SentenceGrounding>, thresholdUsed: Double)` with `val accepted: Boolean get() = status == GroundingStatus.ACCEPTED`
  - `data class GroundingPolicy(similarityThreshold: Double, maxUnsupportedRate: Double)` with `fun verdictStatus(unsupportedRate: Double): GroundingStatus` → `REJECTED` if `unsupportedRate > maxUnsupportedRate` else `ACCEPTED`.

- [ ] **Step 1: Write the failing test**

```kotlin
package github.lms.lemuel.xr.ai.grounding.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GroundingModelsTest {

    @Test
    fun `verdictStatus rejects when rate exceeds max`() {
        val policy = GroundingPolicy(similarityThreshold = 0.7, maxUnsupportedRate = 0.34)
        assertThat(policy.verdictStatus(0.5)).isEqualTo(GroundingStatus.REJECTED)
    }

    @Test
    fun `verdictStatus accepts at or below max`() {
        val policy = GroundingPolicy(similarityThreshold = 0.7, maxUnsupportedRate = 0.34)
        assertThat(policy.verdictStatus(0.34)).isEqualTo(GroundingStatus.ACCEPTED)
        assertThat(policy.verdictStatus(0.0)).isEqualTo(GroundingStatus.ACCEPTED)
    }

    @Test
    fun `accepted convenience reflects status`() {
        val v = GroundingVerdict(GroundingStatus.ACCEPTED, 0.0, emptyList(), 0.7)
        assertThat(v.accepted).isTrue()
        assertThat(v.copy(status = GroundingStatus.REJECTED).accepted).isFalse()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.domain.GroundingModelsTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package github.lms.lemuel.xr.ai.grounding.domain

/** 게이트 판정 상태. NO_EVIDENCE=근거원 미제공, INCONCLUSIVE=평가 불가(임베딩 실패/빈 텍스트). */
enum class GroundingStatus { ACCEPTED, REJECTED, INCONCLUSIVE, NO_EVIDENCE }

/** 문장 하나의 근거성 판정. bestPassageRef 는 가장 유사했던 본문의 reference. */
data class SentenceGrounding(
    val sentence: String,
    val maxSimilarity: Double,
    val grounded: Boolean,
    val bestPassageRef: String?,
)

/** 생성문 전체 판정 결과. unsupportedRate = 미근거 문장 비율. */
data class GroundingVerdict(
    val status: GroundingStatus,
    val unsupportedRate: Double,
    val sentenceResults: List<SentenceGrounding>,
    val thresholdUsed: Double,
) {
    val accepted: Boolean get() = status == GroundingStatus.ACCEPTED
}

/** 판정 규칙: 문장 근거 임계치 + 허용 미근거 비율. */
data class GroundingPolicy(
    val similarityThreshold: Double,
    val maxUnsupportedRate: Double,
) {
    fun verdictStatus(unsupportedRate: Double): GroundingStatus =
        if (unsupportedRate > maxUnsupportedRate) GroundingStatus.REJECTED
        else GroundingStatus.ACCEPTED
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.domain.GroundingModelsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/domain/GroundingModels.kt \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/domain/GroundingModelsTest.kt
git commit -m "feat(grounding): 판정 도메인 값 객체 및 정책 추가"
```

---

## Task 3: SentenceSplitter (application)

**Files:**
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/application/SentenceSplitter.kt`
- Test: `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/application/SentenceSplitterTest.kt`

**Interfaces:**
- Produces: `object SentenceSplitter { fun split(text: String): List<String> }` — splits on sentence-ending punctuation (`. ! ? 。`) followed by whitespace, and on newlines; trims each; drops blanks. Handles a final sentence with no trailing whitespace.

- [ ] **Step 1: Write the failing test**

```kotlin
package github.lms.lemuel.xr.ai.grounding.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SentenceSplitterTest {

    @Test
    fun `splits korean sentences on period and newline`() {
        val text = "하나님은 신실하시다. 그가 너를 붙드신다.\n두려워 말라"
        assertThat(SentenceSplitter.split(text))
            .containsExactly("하나님은 신실하시다.", "그가 너를 붙드신다.", "두려워 말라")
    }

    @Test
    fun `trims and drops blank segments`() {
        assertThat(SentenceSplitter.split("  첫 문장.   \n\n  둘째 문장!  "))
            .containsExactly("첫 문장.", "둘째 문장!")
    }

    @Test
    fun `empty or whitespace text yields empty list`() {
        assertThat(SentenceSplitter.split("   \n  ")).isEmpty()
        assertThat(SentenceSplitter.split("")).isEmpty()
    }

    @Test
    fun `single sentence without trailing punctuation is kept`() {
        assertThat(SentenceSplitter.split("근거 없는 한 문장")).containsExactly("근거 없는 한 문장")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.application.SentenceSplitterTest"`
Expected: FAIL — unresolved `SentenceSplitter`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package github.lms.lemuel.xr.ai.grounding.application

/**
 * 한국어 인식 문장 분리 — 결정론적. 문장 종결부호(. ! ? 。) 뒤 공백 또는 개행에서 자른다.
 * 휴리스틱이므로 병리적 문장부호는 오분할될 수 있다(섀도우 프로토타입 허용 범위).
 */
object SentenceSplitter {
    private val BOUNDARY = Regex("(?<=[.!?。])\\s+|\\n+")

    fun split(text: String): List<String> =
        text.split(BOUNDARY)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.application.SentenceSplitterTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/application/SentenceSplitter.kt \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/application/SentenceSplitterTest.kt
git commit -m "feat(grounding): 한국어 문장 분리기 추가"
```

---

## Task 4: Ports, fake embedder, and EvaluateGroundingUseCase (core)

**Files:**
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/application/port/out/EmbeddingPort.kt`
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/application/port/out/GroundingMetricsPort.kt`
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/application/EvaluateGroundingUseCase.kt`
- Create (test double): `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/application/FakeEmbeddingAdapter.kt`
- Test: `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/application/EvaluateGroundingUseCaseTest.kt`

**Interfaces:**
- Consumes: `CosineSimilarity.cosine` (Task 1), `GroundingStatus/SentenceGrounding/GroundingVerdict/GroundingPolicy` (Task 2), `SentenceSplitter.split` (Task 3).
- Produces:
  - `interface EmbeddingPort { fun embed(texts: List<String>): List<FloatArray> }` — output aligned by index with input; throws on backend failure.
  - `interface GroundingMetricsPort { fun evaluated(purpose: String); fun rejected(purpose: String); fun unsupportedRate(purpose: String, rate: Double); fun inconclusive(purpose: String) }`
  - `class EvaluateGroundingUseCase(embeddings: EmbeddingPort, metrics: GroundingMetricsPort)` with nested `data class Passage(reference: String, text: String)` and `fun evaluate(purpose: String, meditationText: String, passages: List<Passage>, policy: GroundingPolicy): GroundingVerdict`.

- [ ] **Step 1: Write the ports**

```kotlin
// EmbeddingPort.kt
package github.lms.lemuel.xr.ai.grounding.application.port.out

/**
 * 텍스트 → 임베딩 벡터 out-port. 반환은 입력과 같은 순서. 실패 시 예외를 던지며,
 * 호출자(EvaluateGroundingUseCase)가 이를 INCONCLUSIVE 로 처리한다. (DIP: 구현 격리)
 */
interface EmbeddingPort {
    fun embed(texts: List<String>): List<FloatArray>
}
```

```kotlin
// GroundingMetricsPort.kt
package github.lms.lemuel.xr.ai.grounding.application.port.out

/** 근거성 게이트 메트릭 발행 out-port — Micrometer 를 use-case 로부터 격리(DIP). */
interface GroundingMetricsPort {
    fun evaluated(purpose: String)
    fun rejected(purpose: String)
    fun unsupportedRate(purpose: String, rate: Double)
    fun inconclusive(purpose: String)
}
```

- [ ] **Step 2: Write the fake embedder (test double)**

```kotlin
package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort

/**
 * 결정론적 테스트용 임베더. 알려진 텍스트는 지정 벡터로, 미지 텍스트는 onMissing 으로.
 * failNext=true 면 다음 embed 호출에서 예외(임베딩 실패 경로 검증용).
 */
class FakeEmbeddingAdapter(
    private val vectors: Map<String, FloatArray>,
    private val onMissing: (String) -> FloatArray = { floatArrayOf(0f, 0f, 0f) },
) : EmbeddingPort {
    var failNext: Boolean = false

    override fun embed(texts: List<String>): List<FloatArray> {
        if (failNext) throw RuntimeException("embedding backend down")
        return texts.map { vectors[it] ?: onMissing(it) }
    }
}
```

- [ ] **Step 3: Write the failing use-case test**

```kotlin
package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EvaluateGroundingUseCaseTest {

    private val metrics: GroundingMetricsPort = mock()
    private val policy = GroundingPolicy(similarityThreshold = 0.9, maxUnsupportedRate = 0.0)

    // 문장/본문에 명시적 벡터를 부여: grounded 문장은 본문과 동일 벡터(cosine≈1),
    // ungrounded 문장은 직교 벡터(cosine≈0).
    private val grounded = floatArrayOf(1f, 0f)
    private val ungrounded = floatArrayOf(0f, 1f)

    private fun useCase(vectors: Map<String, FloatArray>, fake: FakeEmbeddingAdapter = FakeEmbeddingAdapter(vectors)) =
        EvaluateGroundingUseCase(fake, metrics)

    @Test
    fun `all sentences grounded yields ACCEPTED`() {
        val vectors = mapOf("근거 문장." to grounded, "본문 텍스트" to grounded)
        val verdict = useCase(vectors).evaluate(
            "meditation", "근거 문장.", listOf(Passage("욥 42:5", "본문 텍스트")), policy,
        )
        assertThat(verdict.status).isEqualTo(GroundingStatus.ACCEPTED)
        assertThat(verdict.unsupportedRate).isEqualTo(0.0)
        assertThat(verdict.sentenceResults.single().bestPassageRef).isEqualTo("욥 42:5")
        verify(metrics).evaluated("meditation")
        verify(metrics).unsupportedRate("meditation", 0.0)
    }

    @Test
    fun `an ungrounded sentence yields REJECTED and rejected metric`() {
        val vectors = mapOf("빗나간 문장." to ungrounded, "본문 텍스트" to grounded)
        val verdict = useCase(vectors).evaluate(
            "meditation", "빗나간 문장.", listOf(Passage("욥 42:5", "본문 텍스트")), policy,
        )
        assertThat(verdict.status).isEqualTo(GroundingStatus.REJECTED)
        assertThat(verdict.unsupportedRate).isEqualTo(1.0)
        verify(metrics).rejected("meditation")
    }

    @Test
    fun `empty passages yields NO_EVIDENCE`() {
        val verdict = useCase(emptyMap()).evaluate("meditation", "아무 문장.", emptyList(), policy)
        assertThat(verdict.status).isEqualTo(GroundingStatus.NO_EVIDENCE)
        assertThat(verdict.sentenceResults).hasSize(1)
    }

    @Test
    fun `blank text yields INCONCLUSIVE`() {
        val verdict = useCase(emptyMap()).evaluate("meditation", "   \n ", listOf(Passage("x", "y")), policy)
        assertThat(verdict.status).isEqualTo(GroundingStatus.INCONCLUSIVE)
        verify(metrics).inconclusive("meditation")
    }

    @Test
    fun `embedding failure yields INCONCLUSIVE and never throws`() {
        val fake = FakeEmbeddingAdapter(mapOf("문장." to grounded, "본문" to grounded)).apply { failNext = true }
        val verdict = EvaluateGroundingUseCase(fake, metrics)
            .evaluate("meditation", "문장.", listOf(Passage("r", "본문")), policy)
        assertThat(verdict.status).isEqualTo(GroundingStatus.INCONCLUSIVE)
        verify(metrics).inconclusive("meditation")
    }

    @Test
    fun `mixed grounding computes fractional unsupportedRate`() {
        // 2 문장 중 1 개만 미근거 → rate 0.5. maxUnsupportedRate 0.5 정책이면 ACCEPTED.
        val tolerant = GroundingPolicy(similarityThreshold = 0.9, maxUnsupportedRate = 0.5)
        val vectors = mapOf("좋은 문장." to grounded, "빗나간 문장." to ungrounded, "본문" to grounded)
        val verdict = useCase(vectors).evaluate(
            "meditation", "좋은 문장. 빗나간 문장.", listOf(Passage("r", "본문")), tolerant,
        )
        assertThat(verdict.unsupportedRate).isEqualTo(0.5)
        assertThat(verdict.status).isEqualTo(GroundingStatus.ACCEPTED)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCaseTest"`
Expected: FAIL — `EvaluateGroundingUseCase` unresolved.

- [ ] **Step 5: Write minimal implementation**

```kotlin
package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.CosineSimilarity
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import github.lms.lemuel.xr.ai.grounding.domain.GroundingVerdict
import github.lms.lemuel.xr.ai.grounding.domain.SentenceGrounding
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 섀도우 근거성 게이트 — 생성 묵상의 각 문장이 주어진 성경 본문에 임베딩 근거를 갖는지 판정.
 * 사용자 노출/차단 없음. 판정 + 메트릭만 산출한다. (application: domain + out-port 만 의존, JPA 무접촉)
 *
 * 증거는 scripture 컨텍스트와 결합하지 않도록 로컬 [Passage] 로 받는다(호출자가 매핑).
 */
@Service
class EvaluateGroundingUseCase(
    private val embeddings: EmbeddingPort,
    private val metrics: GroundingMetricsPort,
) {
    data class Passage(val reference: String, val text: String)

    fun evaluate(
        purpose: String,
        meditationText: String,
        passages: List<Passage>,
        policy: GroundingPolicy,
    ): GroundingVerdict {
        metrics.evaluated(purpose)
        val sentences = SentenceSplitter.split(meditationText)
        if (sentences.isEmpty()) {
            metrics.inconclusive(purpose)
            return GroundingVerdict(GroundingStatus.INCONCLUSIVE, 0.0, emptyList(), policy.similarityThreshold)
        }
        if (passages.isEmpty()) {
            return GroundingVerdict(
                GroundingStatus.NO_EVIDENCE,
                1.0,
                sentences.map { SentenceGrounding(it, 0.0, false, null) },
                policy.similarityThreshold,
            )
        }

        val sentenceVecs: List<FloatArray>
        val passageVecs: List<FloatArray>
        try {
            sentenceVecs = embeddings.embed(sentences)
            passageVecs = embeddings.embed(passages.map { it.text })
        } catch (e: Exception) {
            log.warn("임베딩 실패 → INCONCLUSIVE (purpose={}): {}", purpose, e.message)
            metrics.inconclusive(purpose)
            return GroundingVerdict(GroundingStatus.INCONCLUSIVE, 0.0, emptyList(), policy.similarityThreshold)
        }

        val results = sentences.mapIndexed { i, sentence ->
            var best = -1.0
            var bestRef: String? = null
            passageVecs.forEachIndexed { j, pv ->
                val sim = CosineSimilarity.cosine(sentenceVecs[i], pv)
                if (sim > best) {
                    best = sim
                    bestRef = passages[j].reference
                }
            }
            SentenceGrounding(sentence, best, best >= policy.similarityThreshold, bestRef)
        }

        val ungrounded = results.count { !it.grounded }
        val rate = ungrounded.toDouble() / results.size
        metrics.unsupportedRate(purpose, rate)
        val status = policy.verdictStatus(rate)
        if (status == GroundingStatus.REJECTED) metrics.rejected(purpose)
        return GroundingVerdict(status, rate, results, policy.similarityThreshold)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EvaluateGroundingUseCase::class.java)
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCaseTest"`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/application/ \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/application/FakeEmbeddingAdapter.kt \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/application/EvaluateGroundingUseCaseTest.kt
git commit -m "feat(grounding): 근거성 게이트 use-case, 포트, 페이크 임베더 추가"
```

---

## Task 5: Micrometer metrics adapter

**Files:**
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/adapter/out/metrics/MicrometerGroundingMetricsAdapter.kt`
- Test: `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/adapter/out/metrics/MicrometerGroundingMetricsAdapterTest.kt`

**Interfaces:**
- Consumes: `GroundingMetricsPort` (Task 4).
- Produces: `class MicrometerGroundingMetricsAdapter(registry: MeterRegistry) : GroundingMetricsPort` publishing counters `grounding.evaluated`, `grounding.rejected`, `grounding.inconclusive` and a `DistributionSummary` `grounding.unsupported_rate`, all tagged `purpose`.

- [ ] **Step 1: Write the failing test**

```kotlin
package github.lms.lemuel.xr.ai.grounding.adapter.out.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MicrometerGroundingMetricsAdapterTest {

    private val registry = SimpleMeterRegistry()
    private val adapter = MicrometerGroundingMetricsAdapter(registry)

    @Test
    fun `evaluated increments a purpose-tagged counter`() {
        adapter.evaluated("meditation")
        adapter.evaluated("meditation")
        assertThat(registry.get("grounding.evaluated").tag("purpose", "meditation").counter().count())
            .isEqualTo(2.0)
    }

    @Test
    fun `rejected and inconclusive increment their counters`() {
        adapter.rejected("meditation")
        adapter.inconclusive("scene")
        assertThat(registry.get("grounding.rejected").tag("purpose", "meditation").counter().count()).isEqualTo(1.0)
        assertThat(registry.get("grounding.inconclusive").tag("purpose", "scene").counter().count()).isEqualTo(1.0)
    }

    @Test
    fun `unsupportedRate records into a summary`() {
        adapter.unsupportedRate("meditation", 0.25)
        val summary = registry.get("grounding.unsupported_rate").tag("purpose", "meditation").summary()
        assertThat(summary.count()).isEqualTo(1)
        assertThat(summary.totalAmount()).isEqualTo(0.25)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.adapter.out.metrics.MicrometerGroundingMetricsAdapterTest"`
Expected: FAIL — adapter unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package github.lms.lemuel.xr.ai.grounding.adapter.out.metrics

import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * 근거성 게이트 메트릭 Micrometer 어댑터. Grafana 의 *AI 비용·캐시* row 와 나란히 두는
 * `grounding.*` 계열. use-case 는 이 구현을 모른다(DIP).
 */
@Component
class MicrometerGroundingMetricsAdapter(
    private val registry: MeterRegistry,
) : GroundingMetricsPort {

    override fun evaluated(purpose: String) {
        Counter.builder("grounding.evaluated").tag("purpose", purpose).register(registry).increment()
    }

    override fun rejected(purpose: String) {
        Counter.builder("grounding.rejected").tag("purpose", purpose).register(registry).increment()
    }

    override fun inconclusive(purpose: String) {
        Counter.builder("grounding.inconclusive").tag("purpose", purpose).register(registry).increment()
    }

    override fun unsupportedRate(purpose: String, rate: Double) {
        DistributionSummary.builder("grounding.unsupported_rate").tag("purpose", purpose)
            .register(registry).record(rate)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "github.lms.lemuel.xr.ai.grounding.adapter.out.metrics.MicrometerGroundingMetricsAdapterTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/adapter/out/metrics/ \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/adapter/out/metrics/
git commit -m "feat(grounding): Micrometer 메트릭 어댑터 추가"
```

---

## Task 6: Fixtures, real Gemini embedder, and live validation harness

**Files:**
- Create: `backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/adapter/out/embedding/GeminiEmbeddingAdapter.kt`
- Create: `backend/src/test/resources/grounding/orthodox-job.json`
- Create: `backend/src/test/resources/grounding/psalm88-lament.json`
- Create: `backend/src/test/resources/grounding/gnostic-secret-knowledge.json`
- Create: `backend/src/test/resources/grounding/newage-universal-energy.json`
- Create: `backend/src/test/resources/grounding/suffering-justification.json`
- Test: `backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/validation/ScriptureGroundingValidationTest.kt`

**Interfaces:**
- Consumes: `EmbeddingPort` (Task 4), `EvaluateGroundingUseCase` + `Passage` (Task 4), `GroundingPolicy`, `GroundingStatus` (Task 2).
- Produces: `class GeminiEmbeddingAdapter(apiKey: String, model: String) : EmbeddingPort` calling Google Generative Language `embedContent`. No new public API consumed by later tasks.

- [ ] **Step 1: Write the real embedding adapter**

```kotlin
package github.lms.lemuel.xr.ai.grounding.adapter.out.embedding

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Google Generative Language 임베딩 어댑터 — [EmbeddingPort] 구현. 검증 harness 전용.
 * `text-embedding-004:embedContent` 를 텍스트당 1회 호출한다(픽스처 소량이므로 배치 불필요).
 * 키는 GEMINI_API_KEY. 라이브 경로 배선은 이 프로토타입 범위 밖.
 */
@Component
class GeminiEmbeddingAdapter(
    @Value("\${gemini.api-key:\${GEMINI_API_KEY:}}") private val apiKey: String,
    @Value("\${gemini.embedding-model:text-embedding-004}") private val model: String,
) : EmbeddingPort {

    private val client: RestClient = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .build()

    private data class Part(val text: String)
    private data class Content(val parts: List<Part>)
    private data class EmbedRequest(val model: String, val content: Content)
    private data class Embedding(val values: List<Double> = emptyList())
    private data class EmbedResponse(val embedding: Embedding = Embedding())

    override fun embed(texts: List<String>): List<FloatArray> = texts.map { text ->
        val resp = client.post()
            .uri("/models/{m}:embedContent?key={k}", model, apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(EmbedRequest("models/$model", Content(listOf(Part(text)))))
            .retrieve()
            .body(EmbedResponse::class.java) ?: error("빈 임베딩 응답")
        FloatArray(resp.embedding.values.size) { i -> resp.embedding.values[i].toFloat() }
    }
}
```

- [ ] **Step 2: Create the 5 fixtures**

`orthodox-job.json`:
```json
{
  "purpose": "meditation",
  "expectedStatus": "ACCEPTED",
  "meditationText": "고난 중에도 하나님은 여전히 주권자이시다. 욥은 자기 의를 내려놓고 하나님 앞에 잠잠하였다. 내가 주께 대하여 귀로 듣기만 하였으나 이제는 눈으로 주를 뵈옵나이다.",
  "passages": [
    { "reference": "욥 42:5", "text": "내가 주께 대하여 귀로 듣기만 하였사오나 이제는 눈으로 주를 뵈옵나이다" },
    { "reference": "욥 1:21", "text": "여호와께서 주셨고 여호와께서 거두셨으니 여호와의 이름이 찬송을 받으실지니이다" }
  ]
}
```

`psalm88-lament.json`:
```json
{
  "purpose": "meditation",
  "expectedStatus": "ACCEPTED",
  "meditationText": "주여 내가 주야로 부르짖었사오니 내 기도가 주 앞에 이르게 하소서. 어둠이 나의 유일한 친구가 된 밤에도 나는 주를 부릅니다.",
  "passages": [
    { "reference": "시 88:1", "text": "여호와 내 구원의 하나님이여 내가 주야로 주 앞에서 부르짖었사오니" },
    { "reference": "시 88:18", "text": "주는 나의 사랑하는 자와 친구를 내게서 멀리 떠나게 하시며 내가 아는 자를 흑암에 두셨나이다" }
  ]
}
```

`gnostic-secret-knowledge.json`:
```json
{
  "purpose": "meditation",
  "expectedStatus": "REJECTED",
  "meditationText": "참된 구원은 오직 감춰진 비밀 지식을 깨달은 소수에게만 임한다. 물질 세계는 열등한 신이 만든 감옥이며 육체는 영혼의 무덤이다. 너의 내면에 잠든 신성한 불꽃을 스스로 각성시켜라.",
  "passages": [
    { "reference": "욥 42:5", "text": "내가 주께 대하여 귀로 듣기만 하였사오나 이제는 눈으로 주를 뵈옵나이다" },
    { "reference": "욥 1:21", "text": "여호와께서 주셨고 여호와께서 거두셨으니 여호와의 이름이 찬송을 받으실지니이다" }
  ]
}
```

`newage-universal-energy.json`:
```json
{
  "purpose": "meditation",
  "expectedStatus": "REJECTED",
  "meditationText": "우주의 에너지가 당신의 진동수를 높여 풍요를 끌어당긴다. 당신은 곧 우주이며 우주가 곧 당신이다. 긍정의 파동을 우주에 보내면 원하는 현실이 창조된다.",
  "passages": [
    { "reference": "시 88:1", "text": "여호와 내 구원의 하나님이여 내가 주야로 주 앞에서 부르짖었사오니" },
    { "reference": "시 88:18", "text": "주는 나의 사랑하는 자와 친구를 내게서 멀리 떠나게 하시며 내가 아는 자를 흑암에 두셨나이다" }
  ]
}
```

`suffering-justification.json`:
```json
{
  "purpose": "meditation",
  "expectedStatus": "REJECTED",
  "meditationText": "네가 지금 겪는 고통은 모두 네 숨은 죄에 대한 하나님의 형벌이다. 충분히 회개하고 믿음이 강했다면 이런 병과 가난은 오지 않았을 것이다. 고난은 언제나 당사자의 실패를 증명한다.",
  "passages": [
    { "reference": "욥 42:5", "text": "내가 주께 대하여 귀로 듣기만 하였사오나 이제는 눈으로 주를 뵈옵나이다" },
    { "reference": "요 9:3", "text": "예수께서 대답하시되 이 사람이나 그 부모의 죄로 인한 것이 아니라 그에게서 하나님이 하시는 일을 나타내고자 하심이라" }
  ]
}
```

- [ ] **Step 3: Write the live validation harness**

```kotlin
package github.lms.lemuel.xr.ai.grounding.validation

import com.fasterxml.jackson.databind.ObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.embedding.GeminiEmbeddingAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * 라이브 검증 harness — 진짜 Gemini 임베딩으로 5 픽스처를 돌려 게이트의 의미적 판별력을 입증.
 * GEMINI_API_KEY 없으면 자동 비활성화(@EnabledIfEnvironmentVariable). CI 기본 제외용 @Tag.
 *
 * 임계치는 실행 시 튜닝 대상. 아래 정책으로 정통 ACCEPTED / 적대적 REJECTED 분리가 안 되면,
 * 스펙 §7 에 따라 이 테스트를 임계치 스윕 리포트로 바꾸고 precision/recall 을 기록한다.
 */
@Tag("live-embedding")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ScriptureGroundingValidationTest {

    private data class Fixture(
        val purpose: String = "meditation",
        val expectedStatus: String = "",
        val meditationText: String = "",
        val passages: List<Passage> = emptyList(),
    )

    private val mapper = ObjectMapper()
    private val noopMetrics = object : GroundingMetricsPort {
        override fun evaluated(purpose: String) {}
        override fun rejected(purpose: String) {}
        override fun unsupportedRate(purpose: String, rate: Double) {}
        override fun inconclusive(purpose: String) {}
    }

    // 시작 임계치 — 실행 시 조정. 임베딩 모델 바뀌면 재튜닝 필요(스펙 §8).
    private val policy = GroundingPolicy(similarityThreshold = 0.62, maxUnsupportedRate = 0.5)

    private val fixtureNames = listOf(
        "orthodox-job", "psalm88-lament",
        "gnostic-secret-knowledge", "newage-universal-energy", "suffering-justification",
    )

    private fun load(name: String): Fixture =
        javaClass.getResourceAsStream("/grounding/$name.json").use {
            mapper.readValue(it, Fixture::class.java)
        }

    @Test
    fun `real embeddings separate orthodox from heterodox meditations`() {
        val useCase = EvaluateGroundingUseCase(
            GeminiEmbeddingAdapter(apiKey = System.getenv("GEMINI_API_KEY"), model = "text-embedding-004"),
            noopMetrics,
        )
        val report = StringBuilder("\n=== grounding validation ===\n")
        var failures = 0
        fixtureNames.forEach { name ->
            val fx = load(name)
            val verdict = useCase.evaluate(fx.purpose, fx.meditationText, fx.passages, policy)
            val expected = GroundingStatus.valueOf(fx.expectedStatus)
            val ok = verdict.status == expected
            if (!ok) failures++
            report.append(
                "%-28s expected=%-10s got=%-12s rate=%.2f %s\n".format(
                    name, expected, verdict.status, verdict.unsupportedRate, if (ok) "OK" else "MISS",
                ),
            )
        }
        println(report)
        // 스펙 §7: 문서화된 임계치에서 전부 기대 상태와 일치해야 한다.
        assertThat(failures)
            .withFailMessage("일부 픽스처가 기대 상태와 불일치 — 임계치 튜닝 또는 precision/recall 리포트로 전환 필요:%s", report)
            .isZero()
    }
}
```

- [ ] **Step 4: Verify the harness self-disables without a key**

Run (no key): `env -u GEMINI_API_KEY gradle test --tests "github.lms.lemuel.xr.ai.grounding.validation.ScriptureGroundingValidationTest"`
Expected: test is **skipped** (disabled), build succeeds. This proves default CI is unaffected.

- [ ] **Step 5: Run the live validation (requires key) and tune threshold**

Run: `GEMINI_API_KEY=<key> gradle test --tests "github.lms.lemuel.xr.ai.grounding.validation.ScriptureGroundingValidationTest"`
Expected: printed report; test PASSES with all five fixtures matching expected status.
If it does not cleanly separate, adjust `policy.similarityThreshold` (try 0.55–0.75) from the printed per-fixture `rate`. If no single threshold separates them, per spec §7 keep the printed report as the precision/recall deliverable and change the assertion to document the best threshold + its confusion counts instead of `isZero()`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/github/lms/lemuel/xr/ai/grounding/adapter/out/embedding/ \
        backend/src/test/resources/grounding/ \
        backend/src/test/kotlin/github/lms/lemuel/xr/ai/grounding/validation/
git commit -m "feat(grounding): 실 임베딩 어댑터 + 적대적 픽스처 + 라이브 검증 harness 추가"
```

---

## Final verification

- [ ] **Run the full default suite (no live tag) and confirm green**

Run: `gradle test`
Expected: all new unit tests pass; the live validation test is skipped (no key in CI). No pre-existing WIP files staged in any commit.

---

## Self-Review (completed by plan author)

**Spec coverage:**
- §2 sentence-level embedding grounding → Tasks 1,3,4. ✓
- §3.1 domain objects → Tasks 1,2. ✓
- §3.2 use case + splitter + ports → Tasks 3,4. ✓
- §3.3 adapters (fake/gemini/micrometer) → Tasks 4,5,6. ✓
- §3.4 fixtures → Task 6. ✓
- §4 data flow → Task 4 implementation. ✓
- §5 decision rule + NO_EVIDENCE/INCONCLUSIVE/REJECTED/ACCEPTED → Task 4 tests + impl. ✓
- §6 unit + tagged validation harness → Tasks 1–5 (unit) + Task 6 (validation). ✓
- §7 success criteria incl. "if no clean separation, precision/recall report" → Task 6 Step 5. ✓
- §8 limitations noted in code KDoc (validation harness). ✓
- Out-of-scope items (live wiring, DB table, generation re-enable, endpoint) → none added. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code. ✓

**Type consistency:** `EmbeddingPort.embed`, `GroundingMetricsPort` (evaluated/rejected/unsupportedRate/inconclusive), `EvaluateGroundingUseCase.evaluate(purpose, meditationText, passages, policy)`, `Passage(reference, text)`, `GroundingVerdict(status, unsupportedRate, sentenceResults, thresholdUsed)`, `GroundingPolicy(similarityThreshold, maxUnsupportedRate).verdictStatus` — names identical across Tasks 4/5/6. ✓
