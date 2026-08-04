package github.lms.lemuel.xr.ai.grounding.eval

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import github.lms.lemuel.xr.LemuelXrApplication
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * 리포 루트 `eval/grounding/<version>/` 골든셋 로더.
 *
 * 골든셋을 백엔드 테스트 리소스가 아니라 리포 루트에 둔 이유는 `eval/grounding/README.md` §1 참조.
 * 경로 탐색은 `ContentSafetyGateEnforcementTest` 와 같은 방식(코드소스에서 위로 올라가 모듈 루트를 찾고
 * 그 부모를 리포 루트로 본다) — 이 리포에 이미 있는 규약을 따른다.
 */
object GroundingDataset {

    const val DEFAULT_VERSION = "v1"

    private val mapper = jacksonObjectMapper()

    /** 라벨 검토 상태. [SIGNED_OFF] 만 게이트 판정·승격조건 지표에 들어간다. */
    enum class ReviewStatus { SIGNED_OFF, DRAFT }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Review(
        val status: String = "",
        val labeledBy: String = "",
        val labeledAt: String = "",
        val rationale: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Fixture(
        val id: String = "",
        val `class`: String = "",
        val difficulty: String = "",
        val purpose: String = "meditation",
        val expectedStatus: String = "",
        val review: Review = Review(),
        val meditationText: String = "",
        val passages: List<Passage> = emptyList(),
    ) {
        val expected: GroundingStatus get() = GroundingStatus.valueOf(expectedStatus)

        val reviewStatus: ReviewStatus
            get() = when (review.status) {
                "signed_off" -> ReviewStatus.SIGNED_OFF
                "draft" -> ReviewStatus.DRAFT
                else -> error("픽스처 ${id}: 알 수 없는 review.status='${review.status}'")
            }

        val signedOff: Boolean get() = reviewStatus == ReviewStatus.SIGNED_OFF

        /** 임베딩 캐시 워밍용 — 이 픽스처가 필요로 하는 전체 텍스트. */
        fun texts(): List<String> = passages.map { it.text }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ClassSpec(val id: String = "", val expectedStatus: String = "", val note: String = "")

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TunedAgainst(val embeddingModel: String = "", val dimensions: Int = 0, val tunedAt: String = "")

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PinnedPolicy(val similarityThreshold: Double = 0.0, val maxUnsupportedRate: Double = 0.0) {
        fun toPolicy() = GroundingPolicy(similarityThreshold, maxUnsupportedRate)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Targets(val minSignedOff: Int = 0, val minPerClassSignedOff: Int = 0)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Manifest(
        val dataset: String = "",
        val version: String = "",
        val tunedAgainst: TunedAgainst = TunedAgainst(),
        val pinnedPolicy: PinnedPolicy = PinnedPolicy(),
        val classes: List<ClassSpec> = emptyList(),
        val targets: Targets = Targets(),
    )

    data class Loaded(val manifest: Manifest, val fixtures: List<Fixture>) {
        val signedOff: List<Fixture> get() = fixtures.filter { it.signedOff }
        val drafts: List<Fixture> get() = fixtures.filterNot { it.signedOff }

        /** manifest 의 클래스 정의 — id → 기대 상태. */
        fun expectedStatusOf(className: String): String? =
            manifest.classes.firstOrNull { it.id == className }?.expectedStatus
    }

    fun load(version: String = DEFAULT_VERSION): Loaded {
        val dir = datasetDir(version)
        val manifest: Manifest = mapper.readValue(dir.resolve("manifest.json").toFile())
        val fixtureDir = dir.resolve("fixtures")
        check(Files.isDirectory(fixtureDir)) { "픽스처 디렉터리 없음: $fixtureDir" }
        val files = Files.list(fixtureDir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList()
        }
        check(files.isNotEmpty()) { "픽스처가 한 건도 없다: $fixtureDir — 경로 탐색이 깨졌을 수 있다" }
        return Loaded(manifest, files.map { mapper.readValue<Fixture>(it.toFile()) })
    }

    fun datasetDir(version: String = DEFAULT_VERSION): Path {
        val dir = repoRoot().resolve("eval/grounding").resolve(version)
        check(Files.isDirectory(dir)) { "골든셋 디렉터리 없음: $dir" }
        return dir
    }

    fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    /** LemuelXrApplication 코드소스에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    private fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }
}
