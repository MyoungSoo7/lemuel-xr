package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.content.adapter.`in`.web.ProverbsThemeCatalog
import github.lms.lemuel.xr.content.application.port.out.ProverbsInteractionPort
import github.lms.lemuel.xr.content.domain.ProverbsInteraction
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 기준2 잠언 상호작용 — 사용자가 고른 구절 기록 유스케이스.
 *
 * 테마 검증 + recommended_proverbs JSONB 변환 + 도메인 모델 구성을 컨트롤러에서 이관.
 * 알 수 없는 theme 은 E_VALIDATION.
 */
@Service
class RecordProverbsInteractionUseCase(
    private val interactions: ProverbsInteractionPort,
) {

    fun record(
        userId: UUID,
        theme: String?,
        situation: String?,
        chosenProverbRef: String?,
        dimension: String?,
    ): ProverbsInteraction {
        val t = ProverbsThemeCatalog.byKey(theme)
            ?: throw AppException(ErrorCode.E_VALIDATION)

        val interaction = ProverbsInteraction(
            id = null,
            userId = userId,
            userSituation = situation,
            recommendedProverbs = toProverbMaps(t),
            chosenProverbRef = chosenProverbRef,
            chosenDimension = dimension,
            createdAt = LocalDateTime.now(),
        )
        return interactions.save(interaction)
    }

    /** 주제의 구절을 recommended_proverbs (기존 JSONB 형식 [{"ref":..,"text":..}]) 로 변환. */
    private fun toProverbMaps(t: ProverbsThemeCatalog.Theme): List<Map<String, Any?>> =
        t.verses.map { v ->
            linkedMapOf<String, Any?>(
                "ref" to v.ref,
                "text" to v.text,
                "theme" to t.key,
            )
        }
}
