package github.lms.lemuel.xr.emotion.application;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 감정 → Track A 주제(1~7) + Track B 인물(요셉/모세/다윗) 룰 기반 매칭.
 *
 * <p>1차는 룰. pgvector + LLM rerank 는 §5 ProverbCard 등 *상황 맞춤* 매칭에서만 사용.</p>
 *
 * <p>매핑 근거는 docs/TRACK-A-1-4-WISDOM-EMOTION.md, MVP-*.md §11 의 *감정 → Scene*
 * 추천 표 기준.</p>
 */
@Component
public class EmotionRecommender {

    private static final Map<Emotion, List<TopicSuggestion>> TRACK_A = Map.of(
            Emotion.ANXIOUS,    List.of(t(6, "마음을 지키는 것", 0.91), t(4, "시편과 감정", 0.78)),
            Emotion.SAD,        List.of(t(4, "시편과 감정", 0.92), t(5, "고통과 진리", 0.74)),
            Emotion.ANGRY,      List.of(t(2, "잠언과 지혜", 0.83), t(4, "시편과 감정", 0.71)),
            Emotion.CONFUSED,   List.of(t(3, "전도서와 인생", 0.85), t(2, "잠언과 지혜", 0.70)),
            Emotion.LONELY,     List.of(t(4, "시편과 감정", 0.84), t(7, "사람을 두려워하지 않는 것", 0.68)),
            Emotion.EXHAUSTED,  List.of(t(6, "마음을 지키는 것", 0.86), t(1, "일기와 묵상", 0.72)),
            Emotion.GRATEFUL,   List.of(t(1, "일기와 묵상", 0.88), t(4, "시편과 감정", 0.75))
    );

    private static final Map<Emotion, List<CharacterSuggestion>> TRACK_B = Map.of(
            Emotion.ANXIOUS,    List.of(c("MOSES", 4, "두려움 → 동행 인식", 0.83), c("DAVID", 4, "공포·신뢰 통합", 0.70)),
            Emotion.SAD,        List.of(c("DAVID", 1, "시편 23 평온", 0.81)),
            Emotion.ANGRY,      List.of(c("DAVID", 2, "모욕 후 사명 우선", 0.72)),
            Emotion.CONFUSED,   List.of(c("DAVID", 3, "남의 옷을 벗다", 0.78)),
            Emotion.LONELY,     List.of(c("MOSES", 4, "혼자가 아니다", 0.79)),
            Emotion.EXHAUSTED,  List.of(c("MOSES", 1, "광야의 침묵", 0.84)),
            Emotion.GRATEFUL,   List.of(c("JOSEPH", 5, "섭리 회복", 0.81))
    );

    public List<TopicSuggestion> trackA(Emotion e) {
        return TRACK_A.getOrDefault(e, List.of());
    }

    public List<CharacterSuggestion> trackB(Emotion e) {
        return TRACK_B.getOrDefault(e, List.of());
    }

    private static TopicSuggestion t(int id, String title, double match) {
        return new TopicSuggestion(id, title, match);
    }

    private static CharacterSuggestion c(String name, int scene, String reason, double match) {
        return new CharacterSuggestion(name, scene, reason, match);
    }

    public record TopicSuggestion(int topicId, String title, double match) {}
    public record CharacterSuggestion(String character, int scene, String reason, double match) {}
}
