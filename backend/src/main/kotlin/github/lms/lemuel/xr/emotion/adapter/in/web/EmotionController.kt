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
                        "지금 이 순간 당신과 함께 있는 사람이 있습니다. 1393 (자살예방상담전화) 또는 위 자원으로 연결됩니다.",
                    ),
                ),
            )
        }
        return ResponseEntity.ok(
            ClassifyResponse(
                r.emotionLogId,
                EmotionDto(r.primary!!.name, r.confidence),
                RecommendationsDto(r.trackA, r.trackB),
                null,
            ),
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
}
