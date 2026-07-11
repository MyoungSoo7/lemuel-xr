package github.lms.lemuel.xr.emotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.emotion.adapter.out.persistence.EmotionLogJpaEntity;
import github.lms.lemuel.xr.emotion.adapter.out.persistence.EmotionLogRepository;
import github.lms.lemuel.xr.emotion.domain.Emotion;
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner;
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * ClassifyAndRecommendUseCase 단위 테스트 — Mockito 로 협력자를 목킹.
 *
 * <p>핵심 분기:
 * <ul>
 *   <li>위기 키워드 매칭 → AI 분류 *건너뛰고* crisis 응답, 분류/로그 저장 안 함 (R1 safety line)</li>
 *   <li>정상 → 분류 + trackA/trackB 추천 + 로그 영속 (recommendedTrack A/B 분기)</li>
 * </ul>
 * 진짜 {@link EmotionRecommender} + 목킹된 classifier/scanner/logs 사용.</p>
 */
class ClassifyAndRecommendUseCaseTest {

    private final EmotionRecommender recommender = new EmotionRecommender();
    private final ClassifyEmotionUseCase classifier = Mockito.mock(ClassifyEmotionUseCase.class);
    private final EmotionLogRepository logs = Mockito.mock(EmotionLogRepository.class);
    private final CrisisKeywordScanner scanner = Mockito.mock(CrisisKeywordScanner.class);
    private final RecordSafetyAlertUseCase safetyAlert = Mockito.mock(RecordSafetyAlertUseCase.class);

    private final ClassifyAndRecommendUseCase uc =
            new ClassifyAndRecommendUseCase(classifier, recommender, logs, scanner, safetyAlert);

    private final UUID userId = UUID.randomUUID();

    // ───────────────────────── 위기 게이트 (R1 safety line) ─────────────────────────

    @Test
    void 위기_키워드_매칭시_AI_분류_건너뛰고_crisis_응답() {
        var scan = new CrisisKeywordScanner.ScanResult(true, "suicide_intent", "high", "hash");
        when(scanner.scan(any())).thenReturn(scan);
        List<Map<String, Object>> resources = List.of(Map.of("name", "1393"));
        when(safetyAlert.execute(eq(userId), isNull(), eq("emotion_text"), eq(scan)))
                .thenReturn(new RecordSafetyAlertUseCase.Result(true, 42L, resources));

        var r = uc.execute(userId, "죽고 싶어요", "emotional");

        assertThat(r.crisisLockoutRequired()).isTrue();
        assertThat(r.crisisSeverity()).isEqualTo("high");
        assertThat(r.crisisResources()).isEqualTo(resources);
        assertThat(r.primary()).isNull();
        assertThat(r.trackA()).isEmpty();
        // AI 분류·로그 저장 모두 건너뜀 — 위기 자원이 *먼저* 온다.
        verify(classifier, never()).classify(any());
        verify(logs, never()).save(any());
    }

    // ───────────────────────── 정상 흐름 ─────────────────────────

    @Test
    void 정상_ANXIOUS_분류시_trackA_trackB_추천_및_로그저장() {
        when(scanner.scan(any())).thenReturn(CrisisKeywordScanner.ScanResult.none());
        when(classifier.classify("불안해요"))
                .thenReturn(new ClassifyEmotionUseCase.Result(Emotion.ANXIOUS, 0.87));
        // save 시 id 부여 흉내
        when(logs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = uc.execute(userId, "불안해요", "emotional");

        assertThat(r.crisisLockoutRequired()).isFalse();
        assertThat(r.primary()).isEqualTo(Emotion.ANXIOUS);
        assertThat(r.confidence()).isEqualTo(0.87);
        assertThat(r.trackA()).isNotEmpty();
        assertThat(r.trackB()).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("MOSES");

        ArgumentCaptor<EmotionLogJpaEntity> saved = ArgumentCaptor.forClass(EmotionLogJpaEntity.class);
        verify(logs).save(saved.capture());
        EmotionLogJpaEntity log = saved.getValue();
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getClassifiedEmotion()).isEqualTo("ANXIOUS");
        assertThat(log.getChosenDimension()).isEqualTo("emotional");
        // ANXIOUS 는 trackA topic 이 존재 → recommendedTrack "A", recommendedContent "topic:..."
        assertThat(log.getRecommendedTrack()).isEqualTo("A");
        assertThat(log.getRecommendedContent()).startsWith("topic:");
        assertThat(log.getConfidence().doubleValue()).isEqualTo(0.87);
        verify(safetyAlert, never()).execute(any(), any(), any(), any());
    }

    @Test
    void 정상_trackA_비어있으면_recommendedTrack_B_이고_mission_content() {
        // classify 결과가 어떤 emotion 이든 recommender 가 빈 trackA 를 주는 상황을 흉내내기 위해
        // Emotion.values() 중 TRACK_A 에 없는 값을 쓰면 되지만, 모든 값이 매핑돼 있으므로
        // recommender 를 목킹해 trackA=[] / trackB=[mission] 케이스를 직접 만든다.
        EmotionRecommender mockRec = Mockito.mock(EmotionRecommender.class);
        var mission = new EmotionRecommender.CharacterSuggestion("JOSEPH", 1, "이유", 0.9);
        when(mockRec.trackA(any())).thenReturn(List.of());
        when(mockRec.trackB(any())).thenReturn(List.of(mission));

        var localUc = new ClassifyAndRecommendUseCase(classifier, mockRec, logs, scanner, safetyAlert);
        when(scanner.scan(any())).thenReturn(CrisisKeywordScanner.ScanResult.none());
        when(classifier.classify(any()))
                .thenReturn(new ClassifyEmotionUseCase.Result(Emotion.CONFUSED, 0.6));
        when(logs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = localUc.execute(userId, "혼란", null);

        assertThat(r.trackA()).isEmpty();
        ArgumentCaptor<EmotionLogJpaEntity> saved = ArgumentCaptor.forClass(EmotionLogJpaEntity.class);
        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getRecommendedTrack()).isEqualTo("B");
        assertThat(saved.getValue().getRecommendedContent()).isEqualTo("mission:joseph");
        assertThat(saved.getValue().getChosenDimension()).isNull();
    }

    @Test
    void 정상_trackA_trackB_모두_비면_recommendedContent_null() {
        EmotionRecommender mockRec = Mockito.mock(EmotionRecommender.class);
        when(mockRec.trackA(any())).thenReturn(List.of());
        when(mockRec.trackB(any())).thenReturn(List.of());

        var localUc = new ClassifyAndRecommendUseCase(classifier, mockRec, logs, scanner, safetyAlert);
        when(scanner.scan(any())).thenReturn(CrisisKeywordScanner.ScanResult.none());
        when(classifier.classify(any()))
                .thenReturn(new ClassifyEmotionUseCase.Result(Emotion.SAD, 0.5));
        when(logs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = localUc.execute(userId, "슬픔", "spiritual");

        assertThat(r.crisisLockoutRequired()).isFalse();
        ArgumentCaptor<EmotionLogJpaEntity> saved = ArgumentCaptor.forClass(EmotionLogJpaEntity.class);
        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getRecommendedTrack()).isEqualTo("B");
        assertThat(saved.getValue().getRecommendedContent()).isNull();
    }
}
