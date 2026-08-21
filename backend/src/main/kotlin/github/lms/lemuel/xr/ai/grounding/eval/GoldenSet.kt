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
    data class TunedAgainst(
        val embeddingModel: String = "",
        val dimensions: Int = 0,
        val tunedAt: String = "",
        /**
         * 튜닝 공간과 런타임이 어긋나 있음을 **사람이 알고 남겨 둔** 기록. null 이면 어긋난 적이 없다는 뜻이다.
         *
         * 이게 없으면 선택지가 "빨간불로 방치" 아니면 "조용히 재베이스라인" 둘뿐인데, 전자는 모두가
         * 빨간불을 무시하게 만들고 후자는 신호 자체를 지운다. 유예를 *명시적이고 만료되는 형태*로
         * 적어 두는 쪽이 낫다 — 검사는 초록불이되, 차원이 또 바뀌거나 기한이 지나면 다시 빨간불이다.
         */
        val acknowledgedMismatch: AcknowledgedMismatch? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AcknowledgedMismatch(
        /** 이 유예를 적을 당시 런타임이 실제로 쓰던 차원. 지금 설정과 다르면 *새* 드리프트다. */
        val runtimeDimensions: Int = 0,
        val since: String = "",
        val sinceCommit: String = "",
        val measuredAt: String = "",
        val decision: String = "",
        /** `yyyy-MM-dd`. 이 날짜가 지나면 검사가 실패한다. 유예가 조용히 늙는 것을 막는다. */
        val reviewBy: String = "",
        val why: String = "",
        val impact: String = "",
        val blockedOn: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PinnedPolicy(val similarityThreshold: Double = 0.0, val maxUnsupportedRate: Double = 0.0) {
        fun toPolicy() = GroundingPolicy(similarityThreshold, maxUnsupportedRate)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Targets(val minSignedOff: Int = 0, val minPerClassSignedOff: Int = 0)

    /** §4 3단(사람 사인오프)의 주체와 판단 기준. 누가 무슨 기준으로 라벨을 확정했는지의 단일 출처. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SignOff(
        val standard: String = "",
        val authority: String = "",
        /** 사인오프 권한자의 핸들. `labeledBy` 문자열을 이 값으로 조립하므로 임의 표기가 못 들어온다. */
        val authorityHandle: String = "",
        /**
         * 권한자가 3단을 대리에게 맡긴 기록. null 이면 위임이 없었다는 뜻이고, 그때는 `labeledBy` 에
         * `human`/`structural` 외의 값이 올 수 없다. 위임을 *지우는* 게 아니라 *적는* 이유는
         * rules 1번(라벨은 사람이 확정한다)이 실제로 언제 예외 처리됐는지가 남아야 하기 때문이다.
         */
        val delegation: Delegation? = null,
    ) {
        /** 위임 사인오프 픽스처의 `review.labeledBy` 가 가져야 하는 정확한 값. */
        val delegatedLabeledBy: String?
            get() = delegation?.let { "delegated($authorityHandle->${it.delegatedTo})" }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Delegation(
        val date: String = "",
        val delegatedTo: String = "",
        val scope: String = "",
        val record: String = "",
        val notDelegated: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Manifest(
        val dataset: String = "",
        val version: String = "",
        val tunedAgainst: TunedAgainst = TunedAgainst(),
        val pinnedPolicy: PinnedPolicy = PinnedPolicy(),
        val classes: List<ClassSpec> = emptyList(),
        val signOff: SignOff = SignOff(),
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
