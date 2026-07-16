package github.lms.lemuel.xr.game.domain

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode

/**
 * 트랙 B 인물. character 컬럼은 lowercase 문자열.
 *
 * 2026-05-22 mission 재정의 — 인물 우선순위 재정렬:
 * - Stage 1 (MVP — Recovery 사용자 *언어 허락*): JOB · ELIJAH
 * - Stage 2: MOSES (광야 죽음 갈구 → 부름)
 * - Stage 3: DAVID (시편 비탄 + 골리앗)
 * - Stage 4 (Phase 2 회복 후 시야): JOSEPH
 * - Stage 5: JESUS (godjinho 담당)
 */
enum class Character(val dbValue: String) {
    JOB("job"),
    ELIJAH("elijah"),
    MOSES("moses"),
    DAVID("david"),
    JOSEPH("joseph"),
    JESUS("jesus");

    companion object {
        /** path 변수에서 받은 문자열 → enum. 알 수 없으면 E_CHARACTER_UNKNOWN. */
        fun from(value: String?): Character {
            if (value == null) throw AppException(ErrorCode.E_CHARACTER_UNKNOWN)
            for (c in entries) {
                if (c.dbValue.equals(value, ignoreCase = true) || c.name.equals(value, ignoreCase = true)) {
                    return c
                }
            }
            throw AppException(ErrorCode.E_CHARACTER_UNKNOWN, "Unknown character: $value")
        }
    }
}
