package github.lms.lemuel.xr.content.domain

/** Track A 7개 주제 id. */
enum class Topic(val id: Int, val title: String) {
    JOURNAL(1, "일기와 묵상"),
    PROVERBS(2, "잠언과 지혜"),
    ECCLESIASTES(3, "전도서와 인생"),
    PSALMS(4, "시편과 감정"),
    JOB(5, "고통과 진리"),
    HEART(6, "마음을 지키는 것"),
    FEAR(7, "사람을 두려워하지 않는 것"),
    ;

    companion object {
        fun byId(id: Int): Topic =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown topic id: $id")
    }
}
