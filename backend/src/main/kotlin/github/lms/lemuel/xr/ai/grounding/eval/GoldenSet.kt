package github.lms.lemuel.xr.ai.grounding.eval

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase.Passage
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus

/**
 * 근거성 골든셋의 **형태** — 데이터 모델만 담는다. 어디서 어떻게 읽어 오는지는
 * `GoldenSetPort` 구현(어댑터)의 몫이다.
 *
 * 데이터의 원본은 리포 루트 `eval/grounding/<version>/` 이다(그 위치를 고른 이유는 그곳 README §1).
 * 빌드가 그 디렉터리를 `grounding-golden-set/` 아래로 복사해 jar 에 넣으므로
 * (backend/build.gradle.kts 의 processResources), 런타임은 클래스패스만 보면 된다.
 */
object GoldenSet {

    const val DEFAULT_VERSION = "v1"
    const val CLASSPATH_ROOT = "grounding-golden-set"

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
        /** 로드한 파일명(JSON 밖의 정보). 무결성 검증이 id 와 대조한다. */
        val sourceName: String = "",
    ) {
        val expected: GroundingStatus get() = GroundingStatus.valueOf(expectedStatus)

        val reviewStatus: ReviewStatus
            get() = when (review.status) {
                "signed_off" -> ReviewStatus.SIGNED_OFF
                "draft" -> ReviewStatus.DRAFT
                else -> error("픽스처 $id: 알 수 없는 review.status='${review.status}'")
            }

        val signedOff: Boolean get() = reviewStatus == ReviewStatus.SIGNED_OFF

        /** 임베딩 캐시 워밍용 — 이 픽스처가 필요로 하는 근거 본문 전체. */
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

        /** manifest 의 클래스 정의 — id → 기대 상태 문자열. 미정의 클래스면 null. */
        fun expectedStatusOf(className: String): String? =
            manifest.classes.firstOrNull { it.id == className }?.expectedStatus
    }
}
