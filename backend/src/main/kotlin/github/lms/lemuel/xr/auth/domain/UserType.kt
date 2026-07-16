package github.lms.lemuel.xr.auth.domain

/**
 * `dbValue` 는 함수 `dbValue()` 로 노출한다 — 아직 Java 인 호출부(테스트 등)가
 * record/메서드 스타일 `dbValue()` 로 접근하므로 property getter(`getDbValue`) 가 아닌
 * 명시 메서드로 유지해 accessor 호환을 보장한다.
 */
enum class UserType(private val dbValue: String) {
    GUEST("guest"),
    OAUTH_GOOGLE("oauth_google"),
    OAUTH_KAKAO("oauth_kakao");

    fun dbValue(): String = dbValue

    companion object {
        fun from(value: String?): UserType {
            if (value == null) return GUEST
            return entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown user_type: $value")
        }
    }
}
