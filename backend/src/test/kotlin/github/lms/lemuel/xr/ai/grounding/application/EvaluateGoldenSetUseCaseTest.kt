package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetPort
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 채점 use-case 의 계약 검증. **네트워크 무접촉**이라 CI 에서 항상 돈다.
 *
 * 여기서 지키는 것은 수치의 크기가 아니라 *무엇을 채점 대상으로 삼는가* 다 —
 * draft 배제, 고정 정책 선택, 합성 트래픽 격리. 이 셋 중 하나만 어긋나도
 * 매일 나오는 Prometheus 값이 조용히 다른 것을 재게 된다.
 */
class EvaluateGoldenSetUseCaseTest {

    private val grounded = floatArrayOf(1f, 0f)
    private val ungrounded = floatArrayOf(0f, 1f)

    private val vectors = mapOf(
        "정통 문장." to grounded,
        "이단 문장." to ungrounded,
        "초안 문장." to ungrounded,
        "본문" to grounded,
    )

    private fun fixture(
        id: String,
        text: String,
        expected: GroundingStatus,
        status: String = "signed_off",
        passages: List<Passage> = listOf(Passage("욥 42:5", "본문")),
    ) = GoldenSet.Fixture(
        id = id,
        `class` = if (expected == GroundingStatus.REJECTED) "heterodox" else "orthodox",
        difficulty = "medium",
        expectedStatus = expected.name,
        review = GoldenSet.Review(status = status, labeledBy = "human", labeledAt = "2026-08-04"),
        meditationText = text,
        passages = passages,
    )

    private val manifest = GoldenSet.Manifest(
        dataset = "test-set",
        version = "v9",
        pinnedPolicy = GoldenSet.PinnedPolicy(similarityThreshold = 0.9, maxUnsupportedRate = 0.0),
        classes = listOf(
            GoldenSet.ClassSpec("orthodox", "ACCEPTED"),
            GoldenSet.ClassSpec("heterodox", "REJECTED"),
        ),
        targets = GoldenSet.Targets(minSignedOff = 60, minPerClassSignedOff = 10),
    )

    private val fixtures = listOf(
        fixture("ok", "정통 문장.", GroundingStatus.ACCEPTED),
        fixture("bad", "이단 문장.", GroundingStatus.REJECTED),
        fixture("draft-1", "초안 문장.", GroundingStatus.REJECTED, status = "draft"),
    )

    private fun port(
        loaded: GoldenSet.Loaded = GoldenSet.Loaded(manifest, fixtures),
    ) = object : GoldenSetPort {
        var requestedVersion: String? = null
        override fun load(version: String): GoldenSet.Loaded {
            requestedVersion = version
            return loaded
        }
    }

    private fun useCase(
        embeddings: EmbeddingPort = FakeEmbeddingAdapter(vectors),
        goldenSet: GoldenSetPort = port(),
        version: String = GoldenSet.DEFAULT_VERSION,
    ) = EvaluateGoldenSetUseCase(embeddings, goldenSet, version)

    @Test
    fun `사인오프된 표본만 채점하고 draft 는 세어서 제외한다`() {
        val result = useCase().run()

        // draft 를 섞으면 사람이 확정하지 않은 라벨로 P3 을 계산하게 된다 — 그러면 게이트가 자기를 채점한다.
        assertThat(result.outcomes.map { it.id }).containsExactly("ok", "bad")
        assertThat(result.excludedDrafts).isEqualTo(1)
        assertThat(result.summary.sampleCount).isEqualTo(2)
        assertThat(result.summary.mismatches).isEmpty()
        assertThat(result.summary.binary.precision).isEqualTo(1.0)
        assertThat(result.summary.binary.recall).isEqualTo(1.0)
    }

    @Test
    fun `정책 미지정이면 manifest 의 고정 정책을 쓴다`() {
        // 운영에서 실제로 적용 중인 그 값으로 재야 지표가 운영을 설명한다.
        val result = useCase().run()
        assertThat(result.policy).isEqualTo(GroundingPolicy(0.9, 0.0))
        assertThat(result.version).isEqualTo("v9")
    }

    @Test
    fun `정책을 넘기면 그 값으로 채점한다`() {
        // 미근거를 100% 허용하면 이단 픽스처도 통과해 버린다 → 재현율 0. 스윕이 쓰는 경로다.
        val result = useCase().run(GroundingPolicy(similarityThreshold = 0.9, maxUnsupportedRate = 1.0))

        assertThat(result.policy.maxUnsupportedRate).isEqualTo(1.0)
        assertThat(result.summary.binary.recall).isEqualTo(0.0)
        assertThat(result.summary.mismatches).containsExactly("bad")
    }

    @Test
    fun `설정된 버전을 그대로 로더에 넘긴다`() {
        val p = port()
        useCase(goldenSet = p, version = "v7").run()
        assertThat(p.requestedVersion).isEqualTo("v7")
    }

    @Test
    fun `manifest 버전이 비면 요청 버전으로 되돌린다`() {
        // 지표 라벨이 빈 문자열이 되면 어떤 데이터로 잰 값인지 사후에 알 수 없다.
        val loaded = GoldenSet.Loaded(manifest.copy(version = ""), fixtures)
        val result = useCase(goldenSet = port(loaded), version = "v3").run()
        assertThat(result.version).isEqualTo("v3")
    }

    @Test
    fun `같은 본문이 여러 픽스처에 나와도 임베딩은 고유 텍스트당 1회다`() {
        val result = useCase().run()
        // 문장 2 + 공유 본문 1 = 3. 픽스처마다 본문을 다시 태우면 4 가 된다.
        assertThat(result.embeddedTexts).isEqualTo(3)
    }

    @Test
    fun `임베딩이 죽으면 기권으로 잡히고 정밀도는 조작되지 않는다`() {
        val dead = FakeEmbeddingAdapter(vectors).apply { failNext = true }
        val result = useCase(embeddings = dead).run()

        assertThat(result.summary.binary.abstained).isEqualTo(2)
        assertThat(result.summary.binary.precision).isNull()
        assertThat(result.summary.exactAccuracy).isEqualTo(0.0)
    }

    @Test
    fun `로더가 실패하면 사용처가 알도록 그대로 던진다`() {
        // 여기서 삼켜 빈 결과를 내면 "표본 0" 지표가 정상처럼 노출된다.
        val broken = object : GoldenSetPort {
            override fun load(version: String) = error("골든셋 manifest 없음")
        }
        assertThatThrownBy { useCase(goldenSet = broken).run() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("manifest")
    }
}
