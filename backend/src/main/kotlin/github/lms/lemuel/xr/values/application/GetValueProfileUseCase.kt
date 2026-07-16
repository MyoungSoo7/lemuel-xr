package github.lms.lemuel.xr.values.application

import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import github.lms.lemuel.xr.values.application.port.out.UserValueProfilePort
import github.lms.lemuel.xr.values.domain.CdrIndex
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** 사용자 7 가치 프로파일 + 최근 7일 실천 통계 + CDR Index. */
@Service
class GetValueProfileUseCase(
    private val profiles: UserValueProfilePort,
    private val practices: UserValuePracticePort,
) {

    @Transactional(readOnly = true)
    fun execute(userId: UUID): Result {
        val profile = profiles.findByUserId(userId).orElse(null)
        val valuesJson: Map<String, Any?> = profile?.valuesJson ?: emptyMap()

        val sevenDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7)
        val recent = practices.findRecent(userId, sevenDaysAgo)

        // value_id (1~7) 별 카운트
        val countByValue = HashMap<String, Int>()
        for (r in recent) {
            countByValue.merge(r.valueId.toString(), 1, Int::plus)
        }

        val totalPractices = recent.size
        val cdr = CdrIndex.fromPractices(totalPractices)

        return Result(valuesJson, totalPractices, countByValue, cdr.value, cdr.tier)
    }

    data class Result(
        val valuesJson: Map<String, Any?>,
        val totalPractices7d: Int,
        val countByValue: Map<String, Int>,
        val cdrIndex: Int,
        val tier: String,
    )
}
