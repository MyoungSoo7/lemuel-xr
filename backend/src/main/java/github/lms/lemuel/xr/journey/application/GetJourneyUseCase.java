package github.lms.lemuel.xr.journey.application;

import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentRepository;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionJpaEntity;
import github.lms.lemuel.xr.game.adapter.out.persistence.GameSessionRepository;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일주일 여정 추천 — CROSS-MAPPING-VR-AR.md §4.
 *
 * <p>Week N (0 = 첫 주, 1 = 두 번째 주) 의 *인물 + 가치 + 카드* 매핑.</p>
 *
 * <ul>
 *   <li>Week 0: 요셉 (신중함·꾸준함) ↔ Topic 1 (일기) + Topic 2 (잠언)</li>
 *   <li>Week 1: 모세 (인내·동행) ↔ Topic 3 (전도서) + Topic 4 (시편)</li>
 *   <li>Week 2: 다윗 (솔직함·작음) ↔ Topic 1 (일기) + Topic 4 (시편)</li>
 *   <li>Week 3: 예수 (내려놓음·사랑) ↔ Topic 3 (전도서) + Topic 6 (마음)</li>
 *   <li>Week 4+: 사용자가 *자기 7 가치* 빌더로 자율 전환</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GetJourneyUseCase {

    private final UserValuePracticeRepository practices;
    private final GameSessionRepository sessions;
    private final TopicContentRepository cards;

    /** 사용자 시작일로부터 *지금 몇 주째* 인지 + 그 주의 인물·가치. */
    @Transactional(readOnly = true)
    public WeeklyJourney weekly(UUID userId) {
        // 시작일 = 가장 오래된 practice 또는 game_session 의 시각. 없으면 *오늘*.
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1);
        List<UserValuePracticeJpaEntity> past = practices.findRecent(userId, since);
        OffsetDateTime startedAt;
        if (!past.isEmpty()) {
            startedAt = past.get(past.size() - 1).getPracticedAt();
        } else {
            startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        long daysSince = Math.max(0, java.time.Duration.between(startedAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays());
        int weekIndex = (int) (daysSince / 7);
        return planFor(weekIndex);
    }

    /** 오늘의 추천 — 1 인물 + 1 가치 + 1 카드. */
    @Transactional(readOnly = true)
    public DailyPick today(UUID userId) {
        WeeklyJourney week = weekly(userId);
        int valueId = week.values().isEmpty() ? 1 : week.values().get(0);
        TopicContentJpaEntity card = cards
                .findRelevant((short) valueId, null, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        return new DailyPick(
                week.weekIndex(),
                week.character(),
                valueId,
                card == null ? null : new CardSummary(card.getId(), card.getTitle(),
                        card.getScriptureRef(), card.getBody(), card.getAnchorCharacter())
        );
    }

    static WeeklyJourney planFor(int weekIndex) {
        return switch (weekIndex % 4) {
            case 0 -> new WeeklyJourney(weekIndex, "joseph", "신중함·꾸준함", List.of(1, 2));
            case 1 -> new WeeklyJourney(weekIndex, "moses",  "인내·동행",     List.of(3, 4));
            case 2 -> new WeeklyJourney(weekIndex, "david",  "솔직함·작음",   List.of(1, 4));
            case 3 -> new WeeklyJourney(weekIndex, "jesus",  "내려놓음·사랑", List.of(3, 6));
            default -> new WeeklyJourney(weekIndex, "joseph", "신중함·꾸준함", List.of(1, 2));
        };
    }

    public record WeeklyJourney(int weekIndex, String character, String theme, List<Integer> values) {}

    public record DailyPick(int weekIndex, String character, int valueId, CardSummary card) {}

    public record CardSummary(Long id, String title, String scriptureRef, String body, String anchorCharacter) {}
}
