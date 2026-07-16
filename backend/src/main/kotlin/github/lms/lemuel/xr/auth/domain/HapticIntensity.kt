package github.lms.lemuel.xr.auth.domain

/**
 * `dbValue()` 메서드로 노출 — 아직 Java 인 호출부(테스트 등)의 accessor 호환 유지.
 */
enum class HapticIntensity(private val dbValue: String) {
    OFF("off"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    fun dbValue(): String = dbValue

    companion object {
        fun from(value: String?): HapticIntensity {
            if (value == null) return MEDIUM
            return entries.firstOrNull { it.dbValue == value } ?: MEDIUM
        }
    }
}
