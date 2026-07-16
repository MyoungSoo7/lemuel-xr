package github.lms.lemuel.xr.auth.domain

/**
 * 3차원 진입 모드 — Scene/콘텐츠 톤 + 강조점 결정. faith_tone 과 직교.
 *
 * `dbValue()` 메서드로 노출 — 아직 Java 인 호출부(테스트 등)의 accessor 호환 유지.
 */
enum class Dimension(private val dbValue: String) {
    SPIRITUAL("spiritual"),
    EMOTIONAL("emotional"),
    RATIONAL("rational"),
    AUTO("auto");

    fun dbValue(): String = dbValue

    companion object {
        fun from(value: String?): Dimension? {
            if (value == null) return null
            return entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown dimension: $value")
        }
    }
}
