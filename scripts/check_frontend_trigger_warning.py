#!/usr/bin/env python3
"""R4 동의 게이트 배선 판정기 — yml 의 trigger_warning 을 화면이 실제로 읽는가.

배경
────
시나리오 yml 은 정서 부담이 큰 씬에 `trigger_warning` 을 선언한다:

    trigger_warning:
      level: medium
      content: ["betrayal", "family_trauma"]
      consent_card_id: "joseph_scene4_reunion_warning"
      skip_alternative_scene_id: 5

이건 저작자·안전 검토자가 쓰는 자리다. 여기 적으면 화면이 경고 카드를 띄우고
건너뛸 길을 열어 준다 — 는 것이 규약이다.

2026-08-12 전수 조사에서 그 규약이 세 갈래로 깨져 있었다.

  · jesus  — 카드는 있는데 payload 를 안 읽었다. 조건이 `sceneType === "contemplative"`
             였고 레벨·트리거 종류·스킵 목적지가 page.tsx 에 박혀 있었다.
  · david  — 카드가 `extras.violence_warning` 이라는 레거시 boolean 으로 떴다.
             구조화된 trigger_warning 으로 이행하다 만 상태였다.
  · joseph — **카드가 아예 없었다.** yml 은 건너뛸 길(Scene 5)까지 마련해 뒀는데
             화면에 그 문이 없어서, 가족 배신 씬으로 경고 없이 들어갔다.

이 실패는 빨갛지 않다. 화면은 멀쩡히 돌고, 백엔드 테스트도 초록이다 — 그 테스트는
skip 목적지가 실재하는 씬인지만 보지 화면이 그 길을 쓰는지는 보지 않는다. 그래서
안전 검토자가 yml 의 경고를 고치면 **고쳐진 줄 알고** 넘어간다. 아무것도 안 바뀌는데.

이 판정기가 재는 것
───────────────────
  A. `trigger_warning` 을 선언한 시나리오마다, 대응하는 화면이 있으면 그 화면이
     `payload.trigger_warning` 을 (직접 또는 readTriggerWarning 으로) 읽는가.
  B. 그 화면에 건너뛰기 경로(`"skip"` 결정)가 있는가.
  C. 폐기된 레거시 플래그(`violence_warning`)를 게이트 조건으로 다시 읽지 않는가.
  D. **선언 자체가 실어 나를 수 있는 모양인가** — 빈 선언(`trigger_warning:` 만)과
     스칼라 선언(`trigger_warning: true`)을 거부한다. 둘 다 화면 코드는 멀쩡한데
     경고만 사라지는 경로라, 프론트 쪽에서는 잡을 수 없다 (scan_declarations 참조).

이 판정기가 재지 **않는** 것
────────────────────────────
  · 카드가 실제로 렌더되는지. 읽기만 하고 안 그려도 여기서는 통과한다.
    그건 백엔드가 붙은 e2e 만 잡을 수 있다.
  · 문구가 적절한지. 그건 사람(정신건강 검토)이 판정한다.
  · 화면이 없는 인물(예: ruth — Character enum 미등재). NOTE 로 남기고 건너뛴다.
    **건너뛴 건 통과가 아니다.**

rc: 0 = 위반 없음 / 1 = 위반 있음.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCENARIOS = ROOT / "backend" / "src" / "main" / "resources" / "scenarios"
APP = ROOT / "frontend" / "src" / "app"

# `trigger_warning:` 선언 줄. 뒤에 값이 붙었으면 group("inline") 에 잡힌다.
TRIGGER_LINE = re.compile(r"^(?P<indent>\s*)trigger_warning:(?P<inline>.*)$")
SCENE_ID_LINE = re.compile(r"^\s*-\s*id:\s*(\d+)\s*$")
# 주석·빈 줄은 블록 본문 판정에서 건너뛴다.
SKIP_LINE = re.compile(r"^\s*(#.*)?$")

# payload 를 읽는 두 형태 — 직접 접근, 그리고 공용 헬퍼.
READS_PAYLOAD = re.compile(r"payload\.trigger_warning|readTriggerWarning\s*\(")
# 건너뛰기 경로. 결정값이 "skip" 인 곳.
HAS_SKIP = re.compile(r'["\']skip["\']')
# 폐기된 레거시 플래그를 *코드로* 읽는 경우만 잡는다(주석의 언급은 제외).
LEGACY_FLAG = re.compile(r'["\']violence_warning["\']')


def scan_declarations(path: Path) -> tuple[list[int], list[str]]:
    """yml 에서 trigger_warning 선언을 훑는다 → (정상 선언 씬 id 목록, 위반 설명 목록).

    PyYAML 을 쓰지 않는다 — 이 잡(frontend)에는 setup-python 이 붙어 있지 않고
    러너 기본 python3 에 pyyaml 이 있다는 보장이 없다. 줄 단위 스캔으로 충분하다.

    ─────────────── 왜 선언의 *모양* 까지 보나 ───────────────

    프론트에서는 이 두 경우를 구별할 수 없기 때문이다:

      · `trigger_warning:` 키만 적고 값을 비운 경우 — SnakeYAML 이 null 을 싣고
        `spring.jackson.default-property-inclusion: non_null` (application.yml) 이
        그 키를 JSON 에서 통째로 지운다. 화면에 도착하는 모양은 **선언이 아예 없는
        씬과 완전히 같다.** 저작자는 경고를 붙였다고 믿고, 화면엔 아무것도 안 뜨고,
        예전 이 판정기는 "화면이 payload 를 읽는가" 만 봤으므로 초록이었다.
        joseph 사고와 같은 결말이 yml 한 줄로 재현된다.

      · `trigger_warning: true` 같은 스칼라 — 게이트는 열리지만 무엇을 경고하는지
        (level·content·건너뛰기 목적지) 아무것도 못 싣는다. 게다가 예전 정규식은
        줄 끝이 비어야만 매치해서 이 줄은 **선언으로 세지도 않았다** — 그 시나리오는
        판정 대상에서 통째로 빠졌다.

    yml 을 직접 읽는 이 자리가 두 경우를 볼 수 있는 유일한 곳이다.
    """
    ids: list[int] = []
    violations: list[str] = []
    lines = path.read_text(encoding="utf-8").splitlines()
    current: int | None = None

    for i, line in enumerate(lines):
        m = SCENE_ID_LINE.match(line)
        if m:
            current = int(m.group(1))
            continue

        m = TRIGGER_LINE.match(line)
        if not m or current is None:
            continue

        rel = f"{path.name} 씬 {current}"
        inline = m.group("inline").split("#", 1)[0].strip()
        if inline:
            violations.append(
                f"{rel} — `trigger_warning: {inline}` 스칼라 선언\n"
                f"        → level·content·skip_alternative_scene_id 를 실을 수 없다. "
                f"화면에는 무엇을 경고하는지 없는 빈 카드가 뜬다. 블록으로 적어라."
            )
            continue

        # 블록 선언 — 바로 아래에 더 깊은 들여쓰기의 본문이 있어야 한다.
        indent = len(m.group("indent"))
        body = next(
            (ln for ln in lines[i + 1 :] if not SKIP_LINE.match(ln)),
            None,
        )
        if body is None or (len(body) - len(body.lstrip())) <= indent:
            violations.append(
                f"{rel} — `trigger_warning:` 이 비어 있다\n"
                f"        → 값이 null 이면 백엔드 non_null 직렬화가 키를 통째로 지운다. "
                f"화면에는 *선언이 없는 씬과 똑같이* 도착해 경고가 조용히 사라진다."
            )
            continue

        ids.append(current)

    return ids, violations


def main() -> int:
    if not SCENARIOS.is_dir():
        print(f"FAIL  시나리오 디렉터리가 없다: {SCENARIOS.relative_to(ROOT)}")
        return 1

    violations: list[str] = []
    notes: list[str] = []
    checked = 0

    for yml in sorted(SCENARIOS.glob("*.yml")):
        char = yml.stem
        gated, yml_violations = scan_declarations(yml)
        violations.extend(yml_violations)
        if not gated:
            continue

        page = APP / char / "page.tsx"
        if not page.is_file():
            notes.append(
                f"{char} — trigger_warning 씬 {gated} 이 있으나 화면이 없다 "
                f"({page.relative_to(ROOT)} 부재). 판정 건너뜀 — 통과가 아니다."
            )
            continue

        checked += 1
        src = page.read_text(encoding="utf-8")
        rel = page.relative_to(ROOT)

        if not READS_PAYLOAD.search(src):
            violations.append(
                f"{rel}  {char}.yml 씬 {gated} 이 trigger_warning 을 선언했는데 "
                f"화면이 payload 를 읽지 않는다\n"
                f"        → payload.trigger_warning 을 읽어라 "
                f"(공용 게이트: @/components/TriggerWarningGate 의 readTriggerWarning)"
            )
        if not HAS_SKIP.search(src):
            violations.append(
                f"{rel}  {char} — 건너뛰기 경로가 없다\n"
                f"        → 경고만 띄우고 나갈 문을 안 주면 그건 경고가 아니라 통보다. "
                f'결정값 "skip" 으로 다음 씬으로 보내라.'
            )
        if LEGACY_FLAG.search(src):
            violations.append(
                f"{rel}  {char} — 폐기된 레거시 플래그 violence_warning 을 읽는다\n"
                f"        → 구조화된 trigger_warning 만 쓴다. 플래그는 level·content·"
                f"skip 목적지를 못 실어서 yml 개정이 화면에 안 따라온다."
            )

    print(f"R4 동의 게이트 배선 검사 — trigger_warning 선언 시나리오 중 화면 있는 {checked}건 판정")
    print()
    for n in notes:
        print(f"  [NOTE] {n}")
    if notes:
        print()

    if violations:
        for v in violations:
            print(f"  [FAIL] {v}")
        print()
        print(f"--- 위반 {len(violations)} ---")
        return 1

    print("--- 위반 0 ---")
    print("  ⚠️ 이 초록의 주장 범위: 선언이 실어 나를 수 있는 모양이고 화면이")
    print("     payload 를 *읽는다* 까지다. 카드가 실제로 렌더되는지·문구가")
    print("     적절한지는 재지 않는다 — 그건 프론트 유닛 테스트와 사람의 몫이다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
