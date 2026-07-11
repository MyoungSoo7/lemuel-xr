package github.lms.lemuel.xr.auth.domain;

/** 3차원 진입 모드 — Scene/콘텐츠 톤 + 강조점 결정. faith_tone 과 직교. */
public enum Dimension {
    SPIRITUAL("spiritual"),
    EMOTIONAL("emotional"),
    RATIONAL("rational"),
    AUTO("auto");

    private final String dbValue;
    Dimension(String dbValue) { this.dbValue = dbValue; }
    public String dbValue() { return dbValue; }

    public static Dimension from(String value) {
        if (value == null) return null;
        for (Dimension d : values()) if (d.dbValue.equals(value)) return d;
        throw new IllegalArgumentException("Unknown dimension: " + value);
    }
}
