package github.lms.lemuel.xr.game.domain;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;

/** 트랙 B 4 인물. character 컬럼은 lowercase 문자열. */
public enum Character {
    JOSEPH("joseph"),
    MOSES("moses"),
    DAVID("david"),
    JESUS("jesus");

    private final String dbValue;

    Character(String dbValue) { this.dbValue = dbValue; }

    public String dbValue() { return dbValue; }

    /** path 변수에서 받은 문자열 → enum. 알 수 없으면 E_CHARACTER_UNKNOWN. */
    public static Character from(String value) {
        if (value == null) throw new AppException(ErrorCode.E_CHARACTER_UNKNOWN);
        for (Character c : values()) {
            if (c.dbValue.equalsIgnoreCase(value) || c.name().equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new AppException(ErrorCode.E_CHARACTER_UNKNOWN, "Unknown character: " + value);
    }
}
