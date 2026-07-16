package github.lms.lemuel.xr.auth.domain

/**
 * 신앙 톤 강도 — 사용자 노출 콘텐츠의 종교어휘 강도 결정.
 *
 * `dbValue()` 메서드로 노출 — 아직 Java 인 호출부(테스트 등)의 accessor 호환 유지.
 */
enum class FaithTone(private val dbValue: String) {
    STRONG("strong"),     // 신학 용어 + 성경 직접 인용
    BALANCED("balanced"), // 기본 — 균형
    SOFT("soft");         // "지혜의 책" 톤, 비신자도 진입 가능

    fun dbValue(): String = dbValue

    companion object {
        fun from(value: String?): FaithTone {
            if (value == null) return BALANCED
            return entries.firstOrNull { it.dbValue == value } ?: BALANCED
        }
    }
}
