package github.lms.lemuel.xr.auth.domain;

public enum HapticIntensity {
    OFF("off"), LOW("low"), MEDIUM("medium"), HIGH("high");

    private final String dbValue;
    HapticIntensity(String dbValue) { this.dbValue = dbValue; }
    public String dbValue() { return dbValue; }

    public static HapticIntensity from(String value) {
        if (value == null) return MEDIUM;
        for (HapticIntensity h : values()) if (h.dbValue.equals(value)) return h;
        return MEDIUM;
    }
}
