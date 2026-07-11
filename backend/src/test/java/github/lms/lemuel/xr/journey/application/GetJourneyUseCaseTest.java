package github.lms.lemuel.xr.journey.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentRepository;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * journey/application 단위 테스트 — 주차 계산 + 인물/가치 매핑 + 오늘의 카드 추천.
 */
@ExtendWith(MockitoExtension.class)
class GetJourneyUseCaseTest {

    @Mock UserValuePracticeRepository practices;
    @Mock GameSessionRepository sessions;
    @Mock TopicContentRepository cards;

    @InjectMocks GetJourneyUseCase useCase;

    // ─────────────────────────────── planFor ───────────────────────────────

    @Test
    void planFor_4주_로테이션() {
        assertThat(GetJourneyUseCase.planFor(0).character()).isEqualTo("joseph");
        assertThat(GetJourneyUseCase.planFor(0).values()).containsExactly(1, 2);
        assertThat(GetJourneyUseCase.planFor(1).character()).isEqualTo("moses");
        assertThat(GetJourneyUseCase.planFor(1).values()).containsExactly(3, 4);
        assertThat(GetJourneyUseCase.planFor(2).character()).isEqualTo("david");
        assertThat(GetJourneyUseCase.planFor(2).values()).containsExactly(1, 4);
        assertThat(GetJourneyUseCase.planFor(3).character()).isEqualTo("jesus");
        assertThat(GetJourneyUseCase.planFor(3).values()).containsExactly(3, 6);
        // week 4 는 % 4 == 0 → joseph 로테이션, weekIndex 유지.
        assertThat(GetJourneyUseCase.planFor(4).character()).isEqualTo("joseph");
        assertThat(GetJourneyUseCase.planFor(4).weekIndex()).isEqualTo(4);
    }

    // ─────────────────────────────── weekly ───────────────────────────────

    @Test
    void weekly_practice_없으면_week0_joseph() {
        UUID uid = UUID.randomUUID();
        when(practices.findRecent(eq(uid), any(OffsetDateTime.class))).thenReturn(List.of());

        GetJourneyUseCase.WeeklyJourney w = useCase.weekly(uid);

        assertThat(w.weekIndex()).isZero();
        assertThat(w.character()).isEqualTo("joseph");
    }

    @Test
    void weekly_시작일이_15일전이면_week2() {
        UUID uid = UUID.randomUUID();
        // findRecent 결과의 마지막 원소 = 가장 오래된 = 시작일. 15일 전이면 week 2.
        UserValuePracticeJpaEntity oldest = practice(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(15));
        UserValuePracticeJpaEntity recent = practice(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(practices.findRecent(eq(uid), any(OffsetDateTime.class)))
                .thenReturn(List.of(recent, oldest));

        GetJourneyUseCase.WeeklyJourney w = useCase.weekly(uid);

        assertThat(w.weekIndex()).isEqualTo(2);
        assertThat(w.character()).isEqualTo("david");
    }

    // ─────────────────────────────── today ───────────────────────────────

    @Test
    void today_카드있으면_CardSummary_채움() {
        UUID uid = UUID.randomUUID();
        when(practices.findRecent(eq(uid), any(OffsetDateTime.class))).thenReturn(List.of());
        TopicContentJpaEntity card = new TopicContentJpaEntity();
        card.setId(99L);
        card.setTitle("신중함 카드");
        card.setScriptureRef("gen-41:33");
        card.setBody("본문");
        card.setAnchorCharacter("joseph");
        when(cards.findRelevant(anyShort(), any(), any(Pageable.class))).thenReturn(List.of(card));

        GetJourneyUseCase.DailyPick pick = useCase.today(uid);

        assertThat(pick.weekIndex()).isZero();
        assertThat(pick.character()).isEqualTo("joseph");
        assertThat(pick.valueId()).isEqualTo(1); // week0 values.get(0)
        assertThat(pick.card()).isNotNull();
        assertThat(pick.card().id()).isEqualTo(99L);
        assertThat(pick.card().title()).isEqualTo("신중함 카드");
        assertThat(pick.card().scriptureRef()).isEqualTo("gen-41:33");
        assertThat(pick.card().anchorCharacter()).isEqualTo("joseph");
    }

    @Test
    void today_카드없으면_card_null() {
        UUID uid = UUID.randomUUID();
        when(practices.findRecent(eq(uid), any(OffsetDateTime.class))).thenReturn(List.of());
        when(cards.findRelevant(anyShort(), any(), any(Pageable.class))).thenReturn(List.of());

        GetJourneyUseCase.DailyPick pick = useCase.today(uid);

        assertThat(pick.card()).isNull();
        assertThat(pick.valueId()).isEqualTo(1);
    }

    private UserValuePracticeJpaEntity practice(OffsetDateTime at) {
        UserValuePracticeJpaEntity p = new UserValuePracticeJpaEntity();
        p.setPracticedAt(at);
        return p;
    }
}
