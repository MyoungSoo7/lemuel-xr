package github.lms.lemuel.xr.values.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.values.application.port.out.PracticeMetricsPort
import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import github.lms.lemuel.xr.values.domain.UserValuePractice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** 일별 실천 1건 기록. CDR Index 계산의 원천 데이터. */
@Service
class RecordPracticeUseCase(
    private val practices: UserValuePracticePort,
    private val metrics: PracticeMetricsPort,
) {

    @Transactional
    fun execute(
        userId: UUID,
        valueId: Int,
        durationSec: Int?,
        note: String?,
        linkedCharacter: String?,
        linkedGameSession: UUID?,
    ): UserValuePractice {
        if (valueId < 1 || valueId > 7) {
            throw AppException(ErrorCode.E_VALIDATION, "valueId must be 1..7, got $valueId")
        }
        val toSave = UserValuePractice(
            id = null,
            userId = userId,
            valueId = valueId.toShort(),
            practicedAt = OffsetDateTime.now(ZoneOffset.UTC),
            durationSec = durationSec,
            note = note,
            linkedCharacter = linkedCharacter,
            linkedGameSession = linkedGameSession,
        )
        val saved = practices.save(toSave)

        metrics.recordPractice(valueId, linkedCharacter)

        return saved
    }
}
