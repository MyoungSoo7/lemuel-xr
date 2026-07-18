# Scripture-Grounding Shadow Gate — Design

- **Date:** 2026-07-19
- **Status:** In implementation. Tightened 2026-07-19 after an independent spec grade closed 6 ambiguities (metrics `purpose` tag + per-status emission, empty-text/empty-passages precedence, Gemini adapter path, fixture passage shape, cosine edge cases, splitter rule) — all aligned to decisions already made in the implementation plan.
- **Context:** lemuel-xr `ai` bounded context
- **Origin:** Adapts the TraceGuard evidence-gate idea from `Q00/rlm-forge` to lemuel-xr's meditation/Scene generation. TraceGuard's rule — *a parent synthesis may only claim facts a child evidence handle supports* — maps onto *generated meditation text may only claim what a supplied `ScripturePassage` supports*.

## 1. Problem & Goal

lemuel-xr disabled live LLM generation on 2026-05-22 (`ai.generation.enabled=false`) until a clinical advisor is onboarded. Re-enabling it safely requires a *structural* guarantee that generated devotional text does not drift into un-scriptural claims (Gnosticism, New Age, "suffering justification" gaslighting).

Existing safeguards are **heuristic / post-hoc**:
- `lemuel-theology-reviewer` agent — after-the-fact LLM review.
- mental-health-safety lines R1–R5 — keyword matching.

**Goal:** add a **deterministic, pre-synthesis** first-line filter that measures, per sentence, whether a generated meditation is grounded in the scripture passages supplied for that context. Ship it first in **shadow/audit mode** — it produces verdicts and metrics but changes **no** user-facing behavior and does **not** re-enable generation. The output is the evidence base for the "is it safe to re-enable AI generation?" decision.

### Non-goals (YAGNI — explicitly out of scope)
- ✗ No DB audit table (fixtures only; batch audit over real `LlmCache` is a later phase).
- ✗ No wiring into the live `GenerateLlmResponseUseCase` path (decorator integration deferred).
- ✗ No re-enabling of generation.
- ✗ No report endpoint / UI.
- ✗ Does **not** replace `theology-reviewer` or mental-health-safety — it is complementary.

## 2. Approach

Chosen from three options (structured-JSON claims / sentence-embedding grounding / inline citation markers):

**Sentence-level pgvector grounding.** Split generated text into sentences; embed each; compare (cosine) against the embeddings of the supplied `ScripturePassage` texts; a sentence is *grounded* if its max similarity to any passage clears a threshold. Chosen because it runs on existing free-form output without changing the generation prompt (right for shadow mode) and reuses lemuel-xr's planned pgvector infrastructure.

Rejected: structured-JSON claims (depends on strict LLM JSON adherence — observed fragile with `gemini-3-flash-preview`, which returned prose and was hard-rejected in a live `ooo rlm` run); inline citation markers (awkward handling of unmarked sentences).

## 3. Architecture

New sub-package `github.lms.lemuel.xr.ai.grounding`, split by hexagonal layer. Application depends only on domain + out-ports; no JPA; embedding lives behind a port. Complies with `lemuel-hexagonal-enforcer` rules.

### 3.1 Domain (pure, framework-free)
- `GroundingVerdict(accepted: Boolean, unsupportedRate: Double, sentenceResults: List<SentenceGrounding>, thresholdUsed: Double, status: GroundingStatus)`
- `SentenceGrounding(sentence: String, maxSimilarity: Double, grounded: Boolean, bestPassageRef: String?)`
- `GroundingStatus` enum: `ACCEPTED`, `REJECTED`, `INCONCLUSIVE`, `NO_EVIDENCE`
- `GroundingPolicy(similarityThreshold: Double, maxUnsupportedRate: Double)` — decision rule.
- `CosineSimilarity` — pure function `cosine(a: FloatArray, b: FloatArray): Double`. Returns `0.0` (not `NaN`) when either vector has zero magnitude; throws `IllegalArgumentException` on length mismatch.

### 3.2 Application
- `EvaluateGroundingUseCase(embeddingPort, metricsPort)` — orchestrates: split → embed → similarity → assemble verdict → emit metrics → return. `policy` is passed per `evaluate(purpose, meditationText, passages, policy)` call, not injected. **Not a Spring bean** (2026-07-19 decision): nothing consumes it via injection in shadow mode; the harness/tests construct it manually, avoiding eager bean wiring that would break existing `@SpringBootTest` context. Same for the adapters below. Live wiring (bean + config) is a future phase.
- `SentenceSplitter` — Korean-aware, deterministic: splits **after** sentence-ending punctuation `[.!?。]` followed by whitespace, and on newlines; trims; drops empties. (Korean `다.`/`요.` are covered by the generic `.` case.)
- Out-ports (every method carries a `purpose: String` tag so metrics separate `meditation` / `scene` call sites):
  - `EmbeddingPort { fun embed(texts: List<String>): List<FloatArray> }` — output index-aligned with input; throws on backend failure (caller maps to INCONCLUSIVE).
  - `GroundingMetricsPort { fun evaluated(purpose); fun rejected(purpose); fun unsupportedRate(purpose, rate); fun inconclusive(purpose) }`

  Per-status metric emission (every call fires `evaluated` first):

  | terminal status | additional metrics fired |
  | :--- | :--- |
  | ACCEPTED | `unsupportedRate(purpose, rate)` |
  | REJECTED | `unsupportedRate(purpose, rate)`, `rejected(purpose)` |
  | INCONCLUSIVE | `inconclusive(purpose)` |
  | NO_EVIDENCE | (none beyond `evaluated`) |

### 3.3 Adapters
- `FakeEmbeddingAdapter` (test) — deterministic vectors keyed by text, so unit tests are network-free and repeatable.
- `GeminiEmbeddingAdapter` (validation harness / later) — implements `EmbeddingPort` by calling Google Generative Language `text-embedding-004:embedContent` directly (resolved 2026-07-19; not the AI sidecar, which has no embed endpoint). Constructed manually with `(apiKey, model)` by the harness; used only in the tagged validation test, not in CI by default.
- `MicrometerGroundingMetricsAdapter` — `grounding.evaluated` / `grounding.rejected` / `grounding.unsupported_rate` / `grounding.inconclusive`, consistent with the existing `llm.*` metrics on the Grafana *AI 비용·캐시* row.

### 3.4 Fixtures
`backend/src/test/resources/grounding/` — each fixture = `{ purpose, expectedStatus, meditationText, passages: [{ reference, text }] }`. Each passage is exactly `{ reference, text }` — a deliberate subset of the real `scripture/domain/ScripturePassage` (which also has translation/book/chapter/verseStart/verseEnd/tags), matching `EvaluateGroundingUseCase.Passage(reference, text)`. Fixtures:
- `orthodox-job.json` — grounded → ACCEPTED
- `psalm88-lament.json` — grounded lament → ACCEPTED
- `gnostic-secret-knowledge.json` — Gnostic "hidden knowledge" → REJECTED
- `newage-universal-energy.json` — New Age → REJECTED
- `suffering-justification.json` — "your suffering is punishment for sin" gaslighting → REJECTED

## 4. Data Flow (shadow, fixture-driven)

```
fixture[meditationText + passages]
  → SentenceSplitter        → sentences
  → EmbeddingPort.embed(sentences) + EmbeddingPort.embed(passage.text for each)
  → per sentence: maxCosine over all passages
  → SentenceGrounding(grounded = maxCosine ≥ policy.similarityThreshold)
  → unsupportedRate = ungroundedCount / sentenceCount
  → status/accepted per policy (see §5)
  → GroundingMetricsPort emits
  → return GroundingVerdict   (test asserts)
```

## 5. Decision Rule & Error Handling

- **NO_EVIDENCE**: passages empty → cannot ground anything; reported distinctly from REJECTED (do not blame the text when no evidence was supplied).
- **INCONCLUSIVE**: embedding call throws → verdict is INCONCLUSIVE + metric; shadow mode logs and continues, never blocks. (When later wired live, INCONCLUSIVE must fail *open* in shadow, and the live-integration phase decides fail-open vs fail-closed separately.)
- **REJECTED**: `unsupportedRate > policy.maxUnsupportedRate`.
- **ACCEPTED**: otherwise.
- Empty text after splitting → treat as INCONCLUSIVE (nothing to evaluate), not ACCEPTED.
- **Precedence** when multiple conditions hold at once: the empty-text check runs **before** the empty-passages check, so *empty text + empty passages together* yields **INCONCLUSIVE**, not NO_EVIDENCE (nothing to evaluate dominates).
- `grounded` is `maxCosine >= policy.similarityThreshold` (inclusive); `REJECTED` uses strict `>` so `unsupportedRate == maxUnsupportedRate` is ACCEPTED.

## 6. Testing (TDD)

- **Unit** (`FakeEmbeddingAdapter`, deterministic): threshold boundary, aggregation of `unsupportedRate`, all four `GroundingStatus` branches, edge cases (empty text, zero passages, single sentence, mixed grounded/ungrounded), cosine math (orthogonal → 0, identical → 1).
- **Validation harness** (tagged, requires embedding API key, excluded from default CI): run `GeminiEmbeddingAdapter` over the 5 fixtures; assert orthodox fixtures ACCEPTED and adversarial fixtures REJECTED at a documented threshold. This is the proof that the gate has real semantic discrimination, not just correct arithmetic.

## 7. Success Criteria

1. Unit suite green and deterministic.
2. Validation harness: with a documented `similarityThreshold` / `maxUnsupportedRate`, all three adversarial fixtures land REJECTED and both orthodox fixtures land ACCEPTED.
3. If clean separation is **not** achievable on the fixtures, the deliverable is the **measured precision/recall + a threshold recommendation** — an honest negative/partial result is an acceptable outcome for this shadow prototype.

## 8. Known Limitations

- Sentence-embedding grounding is a **coarse first-line filter, not a theology judge.** A heterodox sentence that paraphrases a real verse can score high similarity ("근거 있는 오독" / grounded misreading) and pass. `theology-reviewer` remains required downstream.
- Threshold is corpus- and embedding-model-dependent; must be re-tuned if the embedding model changes.
- Korean sentence splitting is heuristic; pathological punctuation may mis-segment (acceptable for a shadow prototype; note in tests).

## 9. Future Phases (not in this spec)
1. Batch audit over existing `LlmCache` entries (real retrospective data).
2. `TraceGuardedLlmResponseUseCase` decorator wiring into the live path (enforcing mode), gated on clinical-advisor sign-off.
3. Populate/consume the V6 `scripture_embeddings` precomputed vectors instead of embedding passages on the fly.
