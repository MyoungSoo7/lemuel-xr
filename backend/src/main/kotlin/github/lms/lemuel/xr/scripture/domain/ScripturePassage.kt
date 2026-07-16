package github.lms.lemuel.xr.scripture.domain

/**
 * 성경 본문 도메인 모델 — 영속성/웹 프레임워크에 독립적인 불변 값 객체.
 *
 * 영속성 어댑터가 JPA 엔티티를 이 모델로 매핑한다. 포트·use-case 는 엔티티가 아니라
 * 이 도메인 모델을 주고받으므로, 도메인은 영속성 세부사항을 알지 못한다.
 */
data class ScripturePassage(
    val id: Long?,
    val reference: String,
    val translation: String,
    val book: String,
    val bookCode: String?,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int?,
    val text: String,
    val themeTags: Array<String>?,
    val characterTags: Array<String>?,
)
