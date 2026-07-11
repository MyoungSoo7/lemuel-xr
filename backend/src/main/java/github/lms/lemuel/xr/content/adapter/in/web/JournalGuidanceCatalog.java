package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.emotion.domain.Emotion;
import java.util.List;
import java.util.Map;

/**
 * 기준1 (일기와 묵상) — 감정별 <b>성경 기반 규칙형 조언 카탈로그</b>.
 *
 * <p>LLM 을 붙이지 않는다(그건 별도 게이트). 사용자가 고른/감지된 감정에 맞는
 * <b>성경 구절 + 성찰 질문</b> 을 룰로 반환하기 위한 정적 상수.
 *
 * <p>근거는 <b>성경만</b> — 각 감정의 verses·questions 는 시편/잠언/이사야 등 성경 본문뿐.
 * 성경 외 자료(심리학 인용·현대 상담 이론 등)는 이 카탈로그에 포함하지 않는다.
 * (설계 배경인 Pennebaker/DBT 등은 docs 에만 남기고, 사용자 노출 텍스트에는 근거로 쓰지 않는다.)
 *
 * <p>DB 시드 대신 코드 상수 — 성경 본문 참조는 immutable 하고, 감정→조언 매핑은
 * 콘텐츠가 아니라 규칙 축이므로 마이그레이션(조언 시드 테이블) 불필요.
 *
 * <p><b>톤 (R2/R3)</b>: 각 감정은 "그 감정도 성경 안에 이미 있다"는 인증(validation) 으로 시작하되,
 * 정죄·압박(R2 고난 미화 / R3 회복 강요) 문구는 배제. 성찰 질문은 답을 강요하지 않고 여는 형태.
 * {@link Emotion} 재사용으로 EmotionRecommender 와 감정 축을 공유한다.
 */
public final class JournalGuidanceCatalog {

    private JournalGuidanceCatalog() {}

    /**
     * 감정별 성경 기반 조언.
     *
     * @param emotion         감정 enum 키 (ANXIOUS 등)
     * @param emotionLabel    한국어 감정 라벨
     * @param validation      "이 감정도 성경 안에 있다"는 인증 문구 (R2/R3 — 정죄·압박 배제)
     * @param verses          이 감정에 맞는 성경 구절 (성경만 근거)
     * @param reflectionQuestions 성찰 질문 (답을 강요하지 않고 여는 형태)
     */
    public record Guidance(
            String emotion,
            String emotionLabel,
            String validation,
            List<Verse> verses,
            List<String> reflectionQuestions) {}

    /** 성경 구절 — ref(예: psalm-42:5) + 본문(개역개정 톤). 성경 외 인용 금지. */
    public record Verse(String ref, String text) {}

    private static Verse v(String ref, String text) {
        return new Verse(ref, text);
    }

    /**
     * 감정 → 조언 매핑. {@link Emotion} 7종을 모두 덮는다.
     *
     * <p>구절 선택 근거(성경만):
     * <ul>
     *   <li>불안 — 시 34:4, 벧전 5:7, 빌 4:6-7</li>
     *   <li>슬픔 — 시 34:18, 시 42:5, 마 5:4</li>
     *   <li>분노 — 엡 4:26, 잠 15:1, 약 1:19-20</li>
     *   <li>혼란 — 잠 3:5-6, 약 1:5, 시 32:8</li>
     *   <li>외로움 — 시 25:16, 신 31:6, 사 41:10</li>
     *   <li>지침 — 마 11:28, 사 40:31, 시 23:2-3</li>
     *   <li>감사 — 시 103:2, 살전 5:18, 시 100:4</li>
     * </ul>
     */
    private static final Map<Emotion, Guidance> BY_EMOTION = Map.of(
            Emotion.ANXIOUS, new Guidance(
                    "ANXIOUS", "불안",
                    "불안은 믿음 없음의 증거가 아닙니다. 시편의 기도자도 두려워 떨며 하나님을 찾았고, 그 자리에서 응답을 받았습니다.",
                    List.of(
                            v("psalm-34:4", "내가 여호와께 간구하매 내게 응답하시고 내 모든 두려움에서 나를 건지셨도다 (시 34:4)"),
                            v("1pet-5:7", "너희 염려를 다 주께 맡기라 이는 그가 너희를 돌보심이라 (벧전 5:7)"),
                            v("phil-4:6", "아무 것도 염려하지 말고 다만 모든 일에 기도와 간구로 너희 구할 것을 감사함으로 하나님께 아뢰라 (빌 4:6)")),
                    List.of(
                            "지금 가장 크게 떠오르는 염려 하나는 무엇인가요? 그것을 그대로 문장으로 적어봅니다.",
                            "그 염려 중 오늘 내가 어찌할 수 없는 부분과, 한 걸음 해볼 수 있는 부분을 나눠본다면?",
                            "이 염려를 '맡긴다'는 것이 나에게 어떤 모습일지 한 줄로 적어봅니다.")),
            Emotion.SAD, new Guidance(
                    "SAD", "슬픔",
                    "슬픔은 숨겨야 할 것이 아닙니다. 성경은 우는 자와 함께 우시는 하나님을 말하고, 애통하는 자가 복이 있다고 말합니다.",
                    List.of(
                            v("psalm-34:18", "여호와는 마음이 상한 자를 가까이 하시고 충심으로 통회하는 자를 구원하시는도다 (시 34:18)"),
                            v("psalm-42:5", "내 영혼아 네가 어찌하여 낙심하며 어찌하여 내 속에서 불안해 하는가 너는 하나님께 소망을 두라 (시 42:5)"),
                            v("matt-5:4", "애통하는 자는 복이 있나니 그들이 위로를 받을 것임이요 (마 5:4)")),
                    List.of(
                            "오늘 마음을 무겁게 한 일을 애써 밝게 포장하지 않고, 있는 그대로 적어본다면 어떤 말이 될까요?",
                            "이 슬픔을 누구에게(혹은 하나님께) 들려주고 싶은가요?",
                            "지금 나에게 필요한 위로는 무엇인지 한 가지만 적어봅니다.")),
            Emotion.ANGRY, new Guidance(
                    "ANGRY", "분노",
                    "분노 자체가 죄는 아닙니다. 성경은 분을 내어도 죄를 짓지 말라 하며, 감정을 부인하기보다 다스리는 길을 가리킵니다.",
                    List.of(
                            v("eph-4:26", "분을 내어도 죄를 짓지 말며 해가 지도록 분을 품지 말고 (엡 4:26)"),
                            v("prov-15:1", "유순한 대답은 분노를 쉬게 하여도 과격한 말은 노를 격동하느니라 (잠 15:1)"),
                            v("james-1:19", "사람마다 듣기는 속히 하고 말하기는 더디 하며 성내기도 더디 하라 (약 1:19)")),
                    List.of(
                            "무엇이 이 분노를 일으켰나요? 그 아래에 어떤 상처나 두려움이 있는지 살펴봅니다.",
                            "이 감정을 해 지기 전에 어떻게 내려놓고 싶은가요?",
                            "지금 하고 싶은 말 대신, '유순한 대답'으로 바꾼다면 어떤 문장이 될까요?")),
            Emotion.CONFUSED, new Guidance(
                    "CONFUSED", "혼란",
                    "다 알지 못해도 괜찮습니다. 성경은 명철을 의지하지 말고 하나님을 신뢰하라 하며, 지혜가 부족하면 구하라 초대합니다.",
                    List.of(
                            v("prov-3:5", "너는 마음을 다하여 여호와를 신뢰하고 네 명철을 의지하지 말라 (잠 3:5)"),
                            v("james-1:5", "너희 중에 누구든지 지혜가 부족하거든 모든 사람에게 후히 주시는 하나님께 구하라 그리하면 주시리라 (약 1:5)"),
                            v("psalm-32:8", "내가 네 갈 길을 가르쳐 보이고 너를 주목하여 훈계하리로다 (시 32:8)")),
                    List.of(
                            "지금 가장 갈피를 못 잡는 결정 하나는 무엇인가요?",
                            "이미 알고 있는 것과, 아직 모르는 것을 각각 한 가지씩 적어본다면?",
                            "'명철을 의지하지 말라'는 말이 이 상황에서 나에게 어떤 여유를 줄 수 있을까요?")),
            Emotion.LONELY, new Guidance(
                    "LONELY", "외로움",
                    "외로움 속에서도 혼자가 아닙니다. 성경은 함께 계시며 결코 버리지 않으시는 하나님을 반복해서 약속합니다.",
                    List.of(
                            v("psalm-25:16", "주여 나는 외롭고 괴로우니 내게 돌이키사 나에게 은혜를 베푸소서 (시 25:16)"),
                            v("deut-31:6", "그가 너와 함께 가시며 결코 너를 떠나지 아니하시며 버리지 아니하시리니 (신 31:6)"),
                            v("isa-41:10", "두려워하지 말라 내가 너와 함께 함이라 놀라지 말라 나는 네 하나님이 됨이라 (사 41:10)")),
                    List.of(
                            "지금 이 외로움을 가장 크게 느끼는 순간은 언제인가요?",
                            "'함께 계신다'는 약속이 오늘 나에게 닿으려면 어떤 모습이면 좋을까요?",
                            "이번 주에 연결을 시도해볼 수 있는 한 사람이 떠오른다면 적어봅니다.")),
            Emotion.EXHAUSTED, new Guidance(
                    "EXHAUSTED", "지침",
                    "지친 것은 게으름이 아닙니다. 성경은 수고하고 무거운 짐 진 자를 부르시고, 쉼을 주시겠다 초대합니다. 더 해내라 다그치지 않으셔도 됩니다.",
                    List.of(
                            v("matt-11:28", "수고하고 무거운 짐 진 자들아 다 내게로 오라 내가 너희를 쉬게 하리라 (마 11:28)"),
                            v("isa-40:31", "오직 여호와를 앙망하는 자는 새 힘을 얻으리니 독수리가 날개치며 올라감 같을 것이요 (사 40:31)"),
                            v("psalm-23:2", "그가 나를 푸른 풀밭에 누이시며 쉴 만한 물 가로 인도하시는도다 (시 23:2)")),
                    List.of(
                            "지금 나를 가장 지치게 하는 짐 하나는 무엇인가요?",
                            "오늘 내려놓아도 되는 일 한 가지가 있다면 무엇일까요?",
                            "'쉬게 하리라'는 초대를 받아들인다면, 오늘 나에게 필요한 가장 작은 쉼은 무엇인가요?")),
            Emotion.GRATEFUL, new Guidance(
                    "GRATEFUL", "감사",
                    "감사는 억지로 짜내는 의무가 아닙니다. 성경은 받은 은혜를 잊지 말고 기억하며, 그 자리에서 자연스레 흘러나오는 감사를 노래합니다.",
                    List.of(
                            v("psalm-103:2", "내 영혼아 여호와를 송축하며 그의 모든 은택을 잊지 말지어다 (시 103:2)"),
                            v("1thess-5:18", "범사에 감사하라 이것이 그리스도 예수 안에서 너희를 향하신 하나님의 뜻이니라 (살전 5:18)"),
                            v("psalm-100:4", "감사함으로 그의 문에 들어가며 찬송함으로 그의 궁정에 들어가서 그에게 감사하며 그의 이름을 송축할지어다 (시 100:4)")),
                    List.of(
                            "오늘 잊지 않고 기억하고 싶은 '은택' 한 가지는 무엇인가요?",
                            "이 감사를 누구에게 전하고 싶은가요?",
                            "작지만 오늘 감사한 순간을 한 문장으로 남긴다면?")));

    /** 감정 → 조언. 알 수 없는 감정은 {@link Emotion#CONFUSED} 로 fallback (Emotion.fromString 규칙과 동일). */
    public static Guidance forEmotion(Emotion e) {
        return BY_EMOTION.getOrDefault(e, BY_EMOTION.get(Emotion.CONFUSED));
    }

    /** 전체 감정 목록 (프론트 감정 선택지). Emotion enum 순서 유지. */
    public static List<Guidance> all() {
        return List.of(
                BY_EMOTION.get(Emotion.ANXIOUS),
                BY_EMOTION.get(Emotion.SAD),
                BY_EMOTION.get(Emotion.ANGRY),
                BY_EMOTION.get(Emotion.CONFUSED),
                BY_EMOTION.get(Emotion.LONELY),
                BY_EMOTION.get(Emotion.EXHAUSTED),
                BY_EMOTION.get(Emotion.GRATEFUL));
    }

    /** 위기 신호 시 프론트가 일기(#1)/위기 카드로 라우팅하도록 동반하는 상담 자원 문구. */
    public static final List<Map<String, Object>> CRISIS_RESOURCES = List.of(
            Map.of("name", "자살예방상담전화", "contactType", "phone", "contactValue", "1393", "hours", "24시간"),
            Map.of("name", "정신건강위기상담전화", "contactType", "phone", "contactValue", "1577-0199", "hours", "24시간"));
}
