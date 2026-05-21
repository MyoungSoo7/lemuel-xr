package github.lms.lemuel.xr.emotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import org.junit.jupiter.api.Test;

/**
 * 2026-05-22 mission 재정의 — 인물 우선순위 재정렬 반영:
 * Stage 1 (JOB·ELIJAH) 가 MVP. 모세·다윗·요셉은 Phase 2 이후.
 */
class EmotionRecommenderTest {

    private final EmotionRecommender r = new EmotionRecommender();

    @Test
    void ANXIOUS_는_시편_또는_마음_topic_추천() {
        var topics = r.trackA(Emotion.ANXIOUS);
        assertThat(topics).extracting(EmotionRecommender.TopicSuggestion::topicId)
                .contains(4, 6);
    }

    @Test
    void ANXIOUS_는_JOB_또는_ELIJAH_캐릭터_추천() {
        var chars = r.trackB(Emotion.ANXIOUS);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("JOB");
    }

    @Test
    void EXHAUSTED_는_ELIJAH_추천_번아웃_매칭() {
        var chars = r.trackB(Emotion.EXHAUSTED);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("ELIJAH");
    }

    @Test
    void SAD_는_JOB_비탄_매칭() {
        var chars = r.trackB(Emotion.SAD);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("JOB");
    }

    @Test
    void 모든_감정에_대해_TrackA_TrackB_빈_리스트_아님() {
        for (Emotion e : Emotion.values()) {
            assertThat(r.trackA(e)).isNotEmpty();
            assertThat(r.trackB(e)).isNotEmpty();
        }
    }

    @Test
    void JOSEPH_은_Stage_4_로_미루어져_기본_추천에_안_나옴() {
        // R2 가스라이팅 방지 — 회복 모델 (요셉) 을 active 사용자에게 노출 X.
        // Phase 2 에서 "이미 회복된 사용자" 분기에 재활성화 예정.
        for (Emotion e : Emotion.values()) {
            var chars = r.trackB(e);
            assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                    .doesNotContain("JOSEPH");
        }
    }
}
