package github.lms.lemuel.xr.emotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import org.junit.jupiter.api.Test;

/**
 * 2026-05-22 mission 최종 정착 — *영적 비상 대비 훈련* (큐티+민방위).
 * 4 인물 (JOSEPH·MOSES·DAVID·JESUS) 모두 동등한 교사. JOB·ELIJAH 는 보조.
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
    void ANXIOUS_는_MOSES_떨면서_한_발() {
        var chars = r.trackB(Emotion.ANXIOUS);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("MOSES");
    }

    @Test
    void EXHAUSTED_는_MOSES_광야_40년_핵심() {
        var chars = r.trackB(Emotion.EXHAUSTED);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("MOSES");
    }

    @Test
    void GRATEFUL_은_JOSEPH_복귀() {
        // 요셉 복귀 — 4 인물 모두 동등. 감사·회복 결에는 요셉의 *섭리 안의 회복* 핵심.
        var chars = r.trackB(Emotion.GRATEFUL);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("JOSEPH");
    }

    @Test
    void CONFUSED_는_JOSEPH_꿈해석_또는_DAVID_정체성() {
        var chars = r.trackB(Emotion.CONFUSED);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .containsAnyOf("JOSEPH", "DAVID");
    }

    @Test
    void SAD_는_DAVID_시편_비탄() {
        var chars = r.trackB(Emotion.SAD);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("DAVID");
    }

    @Test
    void 모든_감정에_대해_TrackA_TrackB_빈_리스트_아님() {
        for (Emotion e : Emotion.values()) {
            assertThat(r.trackA(e)).isNotEmpty();
            assertThat(r.trackB(e)).isNotEmpty();
        }
    }

    @Test
    void 모든_감정에_4_인물_중_최소_하나는_추천() {
        // 4 인물 동등 원칙 — 사용자가 어떤 감정에 진입해도 4명 중 누군가는 만난다.
        for (Emotion e : Emotion.values()) {
            var chars = r.trackB(e);
            assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                    .containsAnyOf("JOSEPH", "MOSES", "DAVID", "JESUS");
        }
    }
}
