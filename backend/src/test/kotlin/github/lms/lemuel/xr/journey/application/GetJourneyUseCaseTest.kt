package github.lms.lemuel.xr.journey.application

import github.lms.lemuel.xr.content.application.port.out.TopicContentPort
import github.lms.lemuel.xr.content.domain.TopicContent
import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import github.lms.lemuel.xr.values.domain.UserValuePractice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * journey/application 단위 테스트 — 주차 계산 + 인물/가치 매핑 + 오늘의 카드 추천.
 */
class GetJourneyUseCaseTest {

    private val practices: UserValuePracticePort = mock()
    private val cards: TopicContentPort = mock()

    private val useCase = GetJourneyUseCase(practices, cards)

    // ─────────────────────────────── planFor ───────────────────────────────

    @Test
    fun `planFor 4주 로테이션`() {
        assertThat(GetJourneyUseCase.planFor(0).character).isEqualTo("joseph")
        assertThat(GetJourneyUseCase.planFor(0).values).containsExactly(1, 2)
        assertThat(GetJourneyUseCase.planFor(1).character).isEqualTo("moses")
        assertThat(GetJourneyUseCase.planFor(1).values).containsExactly(3, 4)
        assertThat(GetJourneyUseCase.planFor(2).character).isEqualTo("david")
        assertThat(GetJourneyUseCase.planFor(2).values).containsExactly(1, 4)
        assertThat(GetJourneyUseCase.planFor(3).character).isEqualTo("jesus")
        assertThat(GetJourneyUseCase.planFor(3).values).containsExactly(3, 6)
        // week 4 는 % 4 == 0 → joseph 로테이션, weekIndex 유지.
        assertThat(GetJourneyUseCase.planFor(4).character).isEqualTo("joseph")
        assertThat(GetJourneyUseCase.planFor(4).weekIndex).isEqualTo(4)
    }

    // ─────────────────────────────── weekly ───────────────────────────────

    @Test
    fun `weekly practice 없으면 week0 joseph`() {
        val uid = UUID.randomUUID()
        whenever(practices.findRecent(eq(uid), any())).thenReturn(listOf())

        val w = useCase.weekly(uid)

        assertThat(w.weekIndex).isZero()
        assertThat(w.character).isEqualTo("joseph")
    }

    @Test
    fun `weekly 시작일이 15일전이면 week2`() {
        val uid = UUID.randomUUID()
        // findRecent 결과의 마지막 원소 = 가장 오래된 = 시작일. 15일 전이면 week 2.
        val oldest = practice(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15))
        val recent = practice(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
        whenever(practices.findRecent(eq(uid), any())).thenReturn(listOf(recent, oldest))

        val w = useCase.weekly(uid)

        assertThat(w.weekIndex).isEqualTo(2)
        assertThat(w.character).isEqualTo("david")
    }

    // ─────────────────────────────── today ───────────────────────────────

    @Test
    fun `today 카드있으면 CardSummary 채움`() {
        val uid = UUID.randomUUID()
        whenever(practices.findRecent(eq(uid), any())).thenReturn(listOf())
        val card = TopicContent(
            99L, null, "신중함 카드", "gen-41:33", "본문", "joseph", null, null, null,
        )
        whenever(cards.findRelevant(any(), anyOrNull(), any())).thenReturn(listOf(card))

        val pick = useCase.today(uid)

        assertThat(pick.weekIndex).isZero()
        assertThat(pick.character).isEqualTo("joseph")
        assertThat(pick.valueId).isEqualTo(1) // week0 values.get(0)
        assertThat(pick.card).isNotNull()
        assertThat(pick.card!!.id).isEqualTo(99L)
        assertThat(pick.card!!.title).isEqualTo("신중함 카드")
        assertThat(pick.card!!.scriptureRef).isEqualTo("gen-41:33")
        assertThat(pick.card!!.anchorCharacter).isEqualTo("joseph")
    }

    @Test
    fun `today 카드없으면 card null`() {
        val uid = UUID.randomUUID()
        whenever(practices.findRecent(eq(uid), any())).thenReturn(listOf())
        whenever(cards.findRelevant(any(), anyOrNull(), any())).thenReturn(listOf())

        val pick = useCase.today(uid)

        assertThat(pick.card).isNull()
        assertThat(pick.valueId).isEqualTo(1)
    }

    private fun practice(at: OffsetDateTime): UserValuePractice =
        // userId/valueId 는 Kotlin non-null — 테스트는 practicedAt 만 읽으므로 더미 값.
        UserValuePractice(null, UUID.randomUUID(), 1.toShort(), at, null, null, null, null)
}
