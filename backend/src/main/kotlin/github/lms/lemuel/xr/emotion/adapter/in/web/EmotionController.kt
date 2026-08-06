package github.lms.lemuel.xr.emotion.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.emotion.application.ClassifyAndRecommendUseCase
import github.lms.lemuel.xr.emotion.application.EmotionRecommender
import io.micrometer.core.annotation.Timed
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * /api/emotion/classify — 사용자 텍스트 → 감정 분류 + Track A·B 추천.
 *
 * 위기 키워드 감지 시 (R1 safety line) crisisLockout 응답 — 클라이언트는 위기 자원 화면 강제.
 * AI 분류 응답이 *대신* 와서는 안 됨.
 */
@RestController
@RequestMapping("/api/emotion")
@Validated
class EmotionController(
    private val classifyAndRecommend: ClassifyAndRecommendUseCase,
) {

    @PostMapping("/classify")
    @Timed(
        value = "emotion.classify",
        percentiles = [0.5, 0.95, 0.99],
        description = "사용자 텍스트 감정 분류 latency — AI 사이드카 호출 + 추천 매핑 포함",
    )
    fun classify(@Valid @RequestBody req: ClassifyRequest): ResponseEntity<ClassifyResponse> {
        val r = classifyAndRecommend.execute(
            RequestContext.currentUserId(),
            req.text,
            req.context?.preferredMode,
        )
        if (r.crisisLockoutRequired) {
            // 위기 응답 — AI 분류·추천은 모두 null. 클라이언트는 crisisLockout 만 봄.
            return ResponseEntity.ok(
                ClassifyResponse(
                    null, null, null,
                    CrisisLockoutDto(
                        true, r.crisisSeverity, r.crisisResources,
                        "지금 이 순간 당신과 함께 있는 사람이 있습니다. 109 (자살예방 상담전화) 또는 위 자원으로 연결됩니다.",
                    ),
                    null,
                ),
            )
        }
        return ResponseEntity.ok(
            ClassifyResponse(
                r.emotionLogId,
                EmotionDto(r.primary!!.name, r.confidence),
                RecommendationsDto(r.trackA, r.trackB),
                null,
                crisisSupport(r),
            ),
        )
    }

    /**
     * lockout 이 아닌 위기 신호(high/medium)를 분류·추천과 *함께* 실어 보낸다.
     *
     * `crisisLockout` 을 재사용하지 않은 이유: 그 필드는 "클라이언트가 화면을 강제한다" 는
     * 계약이다. required=false 를 얹으면 기존 클라이언트가 위기 화면을 띄울지 말지를
     * 필드 존재 여부가 아니라 내부 불리언으로 판단하게 되고, 한 번 놓치면 그대로 오작동한다.
     * 새 필드는 모르는 클라이언트가 무시해도 기존 동작이 그대로다.
     */
    private fun crisisSupport(r: ClassifyAndRecommendUseCase.Result): CrisisSupportDto? {
        val placement = r.crisisResourcePlacement ?: return null
        return CrisisSupportDto(
            severity = r.crisisSeverity!!,
            placement = placement,
            resources = r.crisisResources,
            gentleMessage = if (placement == ClassifyAndRecommendUseCase.Result.BANNER) {
                "지금 많이 버거우실 수 있습니다. 아래 연결처는 언제든 열려 있습니다."
            } else {
                "혹시 필요하시면, 아래 연결처가 언제든 열려 있습니다."
            },
        )
    }

    data class ClassifyRequest(
        @field:NotBlank @field:Size(max = 1000) val text: String,
        val context: ClassifyContext?,
    )

    data class ClassifyContext(val previousEmotions: List<String>?, val preferredMode: String?)

    data class ClassifyResponse(
        val emotionLogId: Long?,
        val primary: EmotionDto?,
        val recommendations: RecommendationsDto?,
        /** R1 safety lockout — null 이면 정상 응답. non-null 이면 클라이언트는 위기 화면 강제. */
        val crisisLockout: CrisisLockoutDto?,
        /**
         * lockout 은 아니지만 위기 신호가 잡힌 경우(high/medium)의 동반 노출. null 이면 신호 없음.
         * 분류·추천은 정상적으로 함께 온다 — 이 필드는 흐름을 끊지 않고 자원을 *덧붙인다*.
         */
        val crisisSupport: CrisisSupportDto?,
    )

    data class EmotionDto(val emotion: String, val confidence: Double)

    data class RecommendationsDto(
        val trackA: List<EmotionRecommender.TopicSuggestion>,
        val trackB: List<EmotionRecommender.CharacterSuggestion>,
    )

    data class CrisisLockoutDto(
        val required: Boolean,
        val severity: String?,
        val resources: List<Map<String, Any?>>,
        val gentleMessage: String,
    )

    data class CrisisSupportDto(
        val severity: String,
        /** `banner` = 상단 배너(high) · `card` = 하단 조용한 카드(medium). */
        val placement: String,
        val resources: List<Map<String, Any?>>,
        val gentleMessage: String,
    )
}
