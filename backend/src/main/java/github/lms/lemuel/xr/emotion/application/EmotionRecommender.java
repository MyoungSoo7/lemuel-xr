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

    /**
     * 2026-05-22 mission 최종 정착 — *영적 비상 대비 훈련* 정체성.
     *
     * <p>4 인물 (요셉·모세·다윗·예수) 모두 *각자 다른 절망의 결* 을 통과한 동등한 교사.
     * 욥·엘리야는 *보조* 로 유지 — 깊은 비탄·번아웃 결에는 강하지만 *주역 4명* 의 자리는 보존.</p>
     *
     * <p>설계 원칙: 각 감정에 *우선 4 인물 1명 + 보조 1~2명*. 사용자가 *4 인물 모두 만나게* 되는
     * 다양성을 보장.</p>
     */
    private static final Map<Emotion, List<CharacterSuggestion>> TRACK_B = Map.of(
            Emotion.ANXIOUS,    List.of(c("MOSES", 4, "두려움 가운데 떨면서도 한 발", 0.85),
                                        c("DAVID", 4, "공포에 신뢰를 함께 가져가다", 0.78),
                                        c("JOB", 1, "함께 있음의 침묵 (보조)", 0.72)),
            Emotion.SAD,        List.of(c("DAVID", 1, "시편 비탄 — 솔직한 슬픔", 0.88),
                                        c("JESUS", 1, "겟세마네 — 슬픔의 자리 (godjinho)", 0.82),
                                        c("JOB", 2, "태어난 날을 저주할 자유 (보조)", 0.74)),
            Emotion.ANGRY,      List.of(c("MOSES", 3, "다섯 변명을 던지든 가슴에 가져가든", 0.83),
                                        c("DAVID", 2, "엘리압의 비웃음 — 사명 우선", 0.78)),
            Emotion.CONFUSED,   List.of(c("JOSEPH", 1, "꿈 해석 — 답을 찾는 자리", 0.82),
                                        c("DAVID", 3, "사울 갑옷 벗기 — 내 정체성 찾기", 0.79)),
            Emotion.LONELY,     List.of(c("MOSES", 4, "아론과 함께 — 7000명이 남았다", 0.85),
                                        c("DAVID", 1, "시편 23 — 동행의 시인", 0.80),
                                        c("ELIJAH", 5, "혼자가 아니다 (보조)", 0.76)),
            Emotion.EXHAUSTED,  List.of(c("MOSES", 1, "광야 40년의 침묵", 0.86),
                                        c("ELIJAH", 3, "먼저 먹고 자도 됩니다 (보조)", 0.84),
                                        c("JOSEPH", 2, "13년 옥살이의 꾸준함", 0.78)),
            Emotion.GRATEFUL,   List.of(c("JOSEPH", 5, "섭리 안의 회복 — 형제 재회", 0.88),
                                        c("DAVID", 1, "감사의 시편", 0.81))
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
