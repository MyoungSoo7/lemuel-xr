package github.lms.lemuel.xr.journey.application

import github.lms.lemuel.xr.content.application.port.out.TopicContentPort
import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 일주일 여정 추천 — CROSS-MAPPING-VR-AR.md §4.
 *
 * Week N (0 = 첫 주, 1 = 두 번째 주) 의 *인물 + 가치 + 카드* 매핑.
 *
 * - Week 0: 요셉 (신중함·꾸준함) ↔ Topic 1 (일기) + Topic 2 (잠언)
 * - Week 1: 모세 (인내·동행) ↔ Topic 3 (전도서) + Topic 4 (시편)
 * - Week 2: 다윗 (솔직함·작음) ↔ Topic 1 (일기) + Topic 4 (시편)
 * - Week 3: 예수 (내려놓음·사랑) ↔ Topic 3 (전도서) + Topic 6 (마음)
 * - Week 4+: 사용자가 *자기 7 가치* 빌더로 자율 전환
 */
@Service
class GetJourneyUseCase(
    private val practices: UserValuePracticePort,
    private val cards: TopicContentPort,
) {

    /** 사용자 시작일로부터 *지금 몇 주째* 인지 + 그 주의 인물·가치. */
    @Transactional(readOnly = true)
    fun weekly(userId: UUID): WeeklyJourney {
        // 시작일 = 가장 오래된 practice 또는 game_session 의 시각. 없으면 *오늘*.
        val since = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1)
        val past = practices.findRecent(userId, since)
        val startedAt =
            if (past.isNotEmpty()) past[past.size - 1].practicedAt
            else OffsetDateTime.now(ZoneOffset.UTC)
        val daysSince = maxOf(
            0,
            Duration.between(startedAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays(),
        )
        val weekIndex = (daysSince / 7).toInt()
        return planFor(weekIndex)
    }

    /** 오늘의 추천 — 1 인물 + 1 가치 + 1 카드. */
    @Transactional(readOnly = true)
    fun today(userId: UUID): DailyPick {
        val week = weekly(userId)
        val valueId = if (week.values.isEmpty()) 1 else week.values[0]
        val card = cards
            .findRelevant(valueId.toShort(), null, 1)
            .firstOrNull()
        return DailyPick(
            week.weekIndex,
            week.character,
            valueId,
            card?.let {
                CardSummary(it.id, it.title, it.scriptureRef, it.body, it.anchorCharacter)
            },
        )
    }

    data class WeeklyJourney(
        val weekIndex: Int,
        val character: String,
        val theme: String,
        val values: List<Int>,
    )

    data class DailyPick(
        val weekIndex: Int,
        val character: String,
        val valueId: Int,
        val card: CardSummary?,
    )

    data class CardSummary(
        val id: Long?,
        val title: String?,
        val scriptureRef: String?,
        val body: String?,
        val anchorCharacter: String?,
    )

    companion object {
        fun planFor(weekIndex: Int): WeeklyJourney =
            when (weekIndex % 4) {
                0 -> WeeklyJourney(weekIndex, "joseph", "신중함·꾸준함", listOf(1, 2))
                1 -> WeeklyJourney(weekIndex, "moses", "인내·동행", listOf(3, 4))
                2 -> WeeklyJourney(weekIndex, "david", "솔직함·작음", listOf(1, 4))
                3 -> WeeklyJourney(weekIndex, "jesus", "내려놓음·사랑", listOf(3, 6))
                else -> WeeklyJourney(weekIndex, "joseph", "신중함·꾸준함", listOf(1, 2))
            }
    }
}
