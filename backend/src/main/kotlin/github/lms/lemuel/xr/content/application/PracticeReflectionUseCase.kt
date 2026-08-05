package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.application.port.out.PracticeReflectionPort
import github.lms.lemuel.xr.content.domain.PracticeReflection
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * Theme 6/7 실천·성찰 — 작성/조회 유스케이스.
 *
 * R1(법적 의무) 위기 키워드 스캔 + safety_alert 기록 + 도메인 모델 구성을 컨트롤러에서 이관.
 */
@Service
class PracticeReflectionUseCase(
    private val reflections: PracticeReflectionPort,
    private val crisisScanner: CrisisKeywordScanner,
    private val recordSafetyAlert: RecordSafetyAlertUseCase,
) {

    fun create(
        userId: UUID,
        topicId: Short?,
        practiceKind: String?,
        situation: String?,
        reflection: Map<String, Any?>?,
        actionTaken: Boolean?,
        scriptureRef: String?,
        dimension: String?,
    ): CreateResult {
        // R1 (법적 의무): 사용자 자유 기록에 자해·자살 키워드가 있으면 즉시 위기 자원 라우팅.
        val scan = crisisScanner.scan(situation)
        val alert = recordSafetyAlert.execute(userId, null, "practice_$topicId", scan)

        val entry = PracticeReflection(
            id = null,
            userId = userId,
            topicId = topicId,
            practiceKind = practiceKind,
            situation = situation,
            reflection = reflection,
            actionTaken = actionTaken == true,
            scriptureRef = scriptureRef,
            dimension = dimension,
            createdAt = LocalDateTime.now(),
        )
        val saved = reflections.save(entry)

        return CreateResult(saved, alert)
    }

    fun list(userId: UUID, topicId: Short, limit: Int): List<PracticeReflection> =
        reflections.findByUserIdAndTopicIdOrderByCreatedAtDesc(
            // min 만으론 0·음수를 못 막는다 — PageRequest.of 가 예외를 던져 500 이 된다.
            userId, topicId, PageRequest.of(0, limit.coerceIn(1, 100)),
        )

    fun actionCount(userId: UUID, topicId: Short): Long =
        reflections.countByUserIdAndTopicIdAndActionTakenTrue(userId, topicId)

    /** 작성 결과 — 저장된 실천 기록 + safety_alert 결과(위기 라우팅 신호). */
    data class CreateResult(
        val reflection: PracticeReflection,
        val alert: RecordSafetyAlertUseCase.Result,
    )
}
