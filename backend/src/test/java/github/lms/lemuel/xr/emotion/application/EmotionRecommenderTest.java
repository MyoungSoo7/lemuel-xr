package github.lms.lemuel.xr.emotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import org.junit.jupiter.api.Test;

class EmotionRecommenderTest {

    private final EmotionRecommender r = new EmotionRecommender();

    @Test
    void ANXIOUS_는_시편_또는_마음_topic_추천() {
        var topics = r.trackA(Emotion.ANXIOUS);
        assertThat(topics).extracting(EmotionRecommender.TopicSuggestion::topicId)
                .contains(4, 6);
    }

    @Test
    void ANXIOUS_는_MOSES_또는_DAVID_캐릭터_추천() {
        var chars = r.trackB(Emotion.ANXIOUS);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("MOSES");
    }

    @Test
    void GRATEFUL_은_JOSEPH_추천() {
        var chars = r.trackB(Emotion.GRATEFUL);
        assertThat(chars).extracting(EmotionRecommender.CharacterSuggestion::character)
                .contains("JOSEPH");
    }

    @Test
    void 모든_감정에_대해_TrackA_TrackB_빈_리스트_아님() {
        for (Emotion e : Emotion.values()) {
            assertThat(r.trackA(e)).isNotEmpty();
            assertThat(r.trackB(e)).isNotEmpty();
        }
    }
}
