package github.lms.lemuel.xr.content.domain;

/** Track A 7개 주제 id. */
public enum Topic {
    JOURNAL(1, "일기와 묵상"),
    PROVERBS(2, "잠언과 지혜"),
    ECCLESIASTES(3, "전도서와 인생"),
    PSALMS(4, "시편과 감정"),
    JOB(5, "고통과 진리"),
    HEART(6, "마음을 지키는 것"),
    FEAR(7, "사람을 두려워하지 않는 것");

    private final int id;
    private final String title;

    Topic(int id, String title) {
        this.id = id;
        this.title = title;
    }

    public int id() { return id; }
    public String title() { return title; }

    public static Topic byId(int id) {
        for (Topic t : values()) if (t.id == id) return t;
        throw new IllegalArgumentException("Unknown topic id: " + id);
    }
}
