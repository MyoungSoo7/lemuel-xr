package github.lms.lemuel.xr.game.domain;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;

/**
 * 트랙 B 인물. character 컬럼은 lowercase 문자열.
 *
 * <p>2026-05-22 mission 재정의 — 인물 우선순위 재정렬:
 * <ul>
 *   <li>Stage 1 (MVP — Recovery 사용자 *언어 허락*): JOB · ELIJAH</li>
 *   <li>Stage 2: MOSES (광야 죽음 갈구 → 부름)</li>
 *   <li>Stage 3: DAVID (시편 비탄 + 골리앗)</li>
 *   <li>Stage 4 (Phase 2 회복 후 시야): JOSEPH</li>
 *   <li>Stage 5: JESUS (godjinho 담당)</li>
 * </ul>
 * </p>
 */
public enum Character {
    JOB("job"),
    ELIJAH("elijah"),
    MOSES("moses"),
    DAVID("david"),
    JOSEPH("joseph"),
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
