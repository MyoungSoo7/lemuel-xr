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
