package github.lms.lemuel.xr.auth.domain;

/** 신앙 톤 강도 — 사용자 노출 콘텐츠의 종교어휘 강도 결정. */
public enum FaithTone {
    STRONG("strong"),     // 신학 용어 + 성경 직접 인용
    BALANCED("balanced"), // 기본 — 균형
    SOFT("soft");         // "지혜의 책" 톤, 비신자도 진입 가능

    private final String dbValue;
    FaithTone(String dbValue) { this.dbValue = dbValue; }
    public String dbValue() { return dbValue; }

    public static FaithTone from(String value) {
        if (value == null) return BALANCED;
        for (FaithTone t : values()) if (t.dbValue.equals(value)) return t;
        return BALANCED;
    }
}
