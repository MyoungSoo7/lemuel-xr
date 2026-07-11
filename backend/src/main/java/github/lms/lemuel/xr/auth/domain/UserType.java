package github.lms.lemuel.xr.auth.domain;

public enum UserType {
    GUEST("guest"),
    OAUTH_GOOGLE("oauth_google"),
    OAUTH_KAKAO("oauth_kakao");

    private final String dbValue;

    UserType(String dbValue) { this.dbValue = dbValue; }

    public String dbValue() { return dbValue; }

    public static UserType from(String value) {
        if (value == null) return GUEST;
        for (UserType t : values()) if (t.dbValue.equals(value)) return t;
        throw new IllegalArgumentException("Unknown user_type: " + value);
    }
}
