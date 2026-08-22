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
 * 2026-08-20 RUTH 추가 — **이 한 줄이 곧 사용자 노출이다.** 룻은 콘텐츠가 완성된 채로
 * 여기에 없어서 닫혀 있었다(`ScenarioYamlLoader.loadAll()` 은 디렉터리가 아니라
 * `entries` 를 순회한다). 여는 조건은 `docs/RUTH-RUNTIME-SIGNOFF.md` 에 적혀 있고
 * `RuntimeExposureSignoffTest` 가 그걸 집행한다 — 결정 기록 + **AR 폐쇄**.
 * 룻만 AR 이 닫혀 있는 것은 저작이 덜 된 게 아니라 판단이 없어서 내린 결정이다.
 *
 * 2026-08-22 PETER 추가 — 룻과 같은 조건으로 연다. 저작(content/peter/scene1~5.yml)은
 * 완성돼 있었고 여기 한 줄이 없어서 닫혀 있었다. 근거·범위·남은 빚은
 * `docs/PETER-RUNTIME-SIGNOFF.md` 에 있다 — 신학·정신건강 전문 검토는 **없다**.
 * AR 은 룻과 같은 이유로 닫아 둔다(부인의 밤을 사용자의 실제 방에 놓는 판단이 없다).
 *
 * 2026-08-22 DANIEL 추가 — 같은 조건으로 연다. 근거·범위·남은 빚은
 * `docs/DANIEL-RUNTIME-SIGNOFF.md`. 이 인물은 앞의 둘과 **구조가 하나 다르다**:
 * R4 동의 트리거가 2개이고 서로를 상속하지 않는다(Scene 4 금령 고지 / Scene 5 집행).
 * 그래서 건너뛰기 목적지도 두 모양이다 — Scene 4 는 정수(다음 씬 점프),
 * Scene 5 는 마지막 씬이라 문자열(같은 씬의 conditional_blocks 축약본).
 * 저작(`content/daniel/README.md`)이 건 **인간 안전검토자 사인오프는 없다** —
 * 해제하지 않고 대장에 미해소 부채로 등재한 채 연다.
 *
 * 2026-08-22 ESTHER 추가 — 같은 조건으로 연다. 근거·범위·남은 빚은
 * `docs/ESTHER-RUNTIME-SIGNOFF.md`. 이 인물의 축은 **개시(disclosure)의 자기 결정권**이고,
 * 「드러내는 것이 정답인 묵상이 아니다」가 R3 로 집행된다 — 세 카드에 등급이 없다.
 * 구조는 다니엘과 또 다르다: R4 동의 트리거가 **하나뿐이고 Scene 1 에 있다**.
 * 저작은 건너뛰기를 같은 파일 안의 대체 라우트로 설계했지만 엔진은 문자열 목적지를
 * 마지막 Scene 에서만 허용하므로(SceneSkipResolver), 목적지를 정수 2로 두고 저작이 그
 * 라우트에 담아 둔 자막을 `skip_bridge_narration_ko` 로 옮겼다. 그래서 **새로 쓴 산문이
 * 한 줄도 없다** — 다니엘의 넉 줄(미검토 신작)과 여기가 갈린다.
 * 저작이 건 인간 안전검토자 사인오프는 다니엘과 같이 **없다** — 등재한 채로 연다.
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
    SOLOMON("solomon"),
    RUTH("ruth"),
    PETER("peter"),
    DANIEL("daniel"),
    ESTHER("esther");

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
