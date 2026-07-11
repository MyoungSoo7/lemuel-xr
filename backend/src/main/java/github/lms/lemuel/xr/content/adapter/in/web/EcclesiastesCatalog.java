package github.lms.lemuel.xr.content.adapter.in.web;

import java.util.List;

/**
 * 기준4 — 인생에 관한 전도서의 진리를 <b>카테고리로 분류</b>한 정적 카탈로그.
 *
 * <p>근거는 <b>성경만</b> — 각 카테고리의 chapterRef·verse·성구는 전도서 + 관련 성구뿐.
 * 성경 외 자료(실존철학 등 현대 비교)는 이 카탈로그에 포함하지 않는다.
 *
 * <p>DB 시드 대신 코드 상수로 둔다 — 성경 본문 참조는 immutable 하고, 카테고리 분류는
 * 콘텐츠가 아니라 UI 분류 축이므로 마이그레이션(카테고리 시드 테이블) 불필요.
 *
 * <p><b>톤 (R2)</b>: 각 카테고리는 "해 아래 유한함의 정직한 인정"으로 시작하되,
 * SEASONS 의 계절은 전 3:1-8 의 짝(때와 때)을 그대로 따르고, 전체 흐름은 전 12:13 결론으로 향한다.
 * 절망·허무 강화가 아니라 창조주 경외로 마무리. 고난 미화·방치(R2 위반) 문구 배제.
 */
public final class EcclesiastesCatalog {

    private EcclesiastesCatalog() {}

    /**
     * 인생 진리 카테고리.
     *
     * @param key         분류 키
     * @param title       카테고리 제목
     * @param chapterRef  ecclesiastes_views.chapter_ref 로 저장될 전도서 본문 참조
     * @param verse       대표 성구 (전도서 또는 관련 성구)
     * @param honestNote  "해 아래 유한함" 을 정직하게 인정하는 문구 (헛됨 축)
     * @param meaningNote 그 유한함 너머 — 창조주 경외로 향하는 문구 (의미 축)
     */
    public record Category(
            String key,
            String title,
            String chapterRef,
            String verse,
            String honestNote,
            String meaningNote) {}

    /** 전 3:1-8 "범사에 기한이 있다" — 인생 계절 선택지 (때와 때의 짝). */
    public record Season(String key, String label, String verse) {}

    public static final List<Category> CATEGORIES = List.of(
            new Category(
                    "vanity",
                    "모든 것이 헛되다 — 유한함의 정직한 인정",
                    "eccl-1:2-11",
                    "전도자가 이르되 헛되고 헛되며 헛되고 헛되니 모든 것이 헛되도다 (전 1:2)",
                    "해 아래 수고가 영원히 남지 않음을, 애써 부인하지 않고 그대로 인정해도 괜찮습니다.",
                    "헛됨의 인정은 절망의 끝이 아니라, 해 위의 하나님을 바라보는 시작이 됩니다."),
            new Category(
                    "seasons",
                    "범사에 기한이 있다 — 인생의 계절",
                    "eccl-3:1-8",
                    "범사에 기한이 있고 천하 만사가 다 때가 있나니 (전 3:1)",
                    "지금이 심는 때든 뽑는 때든, 웃는 때든 우는 때든, 그 계절이 지나갈 것임을 인정합니다.",
                    "때를 주관하시는 분이 하나님이심을 기억할 때, 지금의 계절을 견딜 힘을 얻습니다 (전 3:11)."),
            new Category(
                    "toil",
                    "수고의 몫 — 손에 쥔 오늘",
                    "eccl-2:24-26",
                    "사람이 먹고 마시며 수고하는 것보다 그의 마음을 더 기쁘게 하는 것은 없나니 이것도 하나님의 손에서 나오는 것이니라 (전 2:24)",
                    "내일을 다 통제할 수 없고, 수고의 결과를 다 소유할 수 없음을 인정합니다.",
                    "오늘의 일용할 몫을 하나님의 선물로 받는 것 — 그것이 해 아래에서 주어진 분깃입니다."),
            new Category(
                    "wisdom_limit",
                    "지혜의 한계 — 다 알 수 없음",
                    "eccl-7:13-14",
                    "하나님께서 행하시는 일을 보라 하나님께서 굽게 하신 것을 누가 능히 곧게 하겠느냐 (전 7:13)",
                    "형통한 날에도 곤고한 날에도, 인생의 앞일을 다 헤아릴 수 없음을 정직하게 받아들입니다.",
                    "다 알지 못해도, 이 모든 때를 지으신 분을 신뢰하는 자리로 나아갑니다 (전 7:14)."),
            new Category(
                    "mortality",
                    "돌아갈 곳 — 유한한 생명",
                    "eccl-12:1-7",
                    "너는 청년의 때에 너의 창조주를 기억하라 (전 12:1)",
                    "생명이 유한하다는 사실을, 두려움으로 밀어내지 않고 조용히 인정합니다.",
                    "흙은 땅으로, 영은 그것을 주신 하나님께로 돌아감을 기억할 때 오늘이 선물이 됩니다 (전 12:7)."),
            new Category(
                    "conclusion",
                    "일의 결국 — 하나님을 경외하라",
                    "eccl-12:13-14",
                    "일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다 "
                            + "이것이 모든 사람의 본분이니라 (전 12:13)",
                    "헛됨을 인정한 그 자리에서 멈추지 않고, 결국까지 정직하게 듣습니다.",
                    "전도서의 헛됨은 여기, 창조주를 경외하는 결론에서 마무리됩니다 — 이것이 인생의 본분입니다."));

    /** 전 3:1-8 의 계절 짝 — 사용자가 자신의 '인생 계절'을 고르는 선택지. */
    public static final List<Season> SEASONS = List.of(
            new Season("planting", "심을 때", "심을 때가 있고 심은 것을 뽑을 때가 있으며 (전 3:2)"),
            new Season("uprooting", "뽑을 때", "심을 때가 있고 심은 것을 뽑을 때가 있으며 (전 3:2)"),
            new Season("weeping", "울 때", "울 때가 있고 웃을 때가 있으며 (전 3:4)"),
            new Season("laughing", "웃을 때", "울 때가 있고 웃을 때가 있으며 (전 3:4)"),
            new Season("mourning", "슬퍼할 때", "슬퍼할 때가 있고 춤출 때가 있으며 (전 3:4)"),
            new Season("dancing", "춤출 때", "슬퍼할 때가 있고 춤출 때가 있으며 (전 3:4)"),
            new Season("keeping", "지킬 때", "지킬 때가 있고 버릴 때가 있으며 (전 3:6)"),
            new Season("letting_go", "버릴 때", "지킬 때가 있고 버릴 때가 있으며 (전 3:6)"),
            new Season("silence", "잠잠할 때", "잠잠할 때가 있고 말할 때가 있으며 (전 3:7)"),
            new Season("speaking", "말할 때", "잠잠할 때가 있고 말할 때가 있으며 (전 3:7)"));
}
