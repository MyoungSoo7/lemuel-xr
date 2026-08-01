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
 *
 * 2026-08-02 Track B 확장 — SOLOMON 추가 (성공 속 허무·실존적 공허 재정향, MVP-SOLOMON.md).
 *
 * 주의: 값을 추가하면 resources/scenarios/{dbValue}.yml 이 *반드시* 함께 있어야 한다.
 * ScenarioYamlLoader 는 파일이 없으면 warn 로그만 남기고 조용히 건너뛴다 —
 * ScenarioYamlLoaderTest 의 `모든 Character 에 시나리오 yml 존재` 가 그 구멍을 막는다.
 */
enum class Character(val dbValue: String) {
    JOB("job"),
    ELIJAH("elijah"),
    MOSES("moses"),
    DAVID("david"),
    JOSEPH("joseph"),
    JESUS("jesus"),
    SOLOMON("solomon");

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
