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

TRIGGER_LINE = re.compile(r"^\s*trigger_warning:\s*$")
SCENE_ID_LINE = re.compile(r"^\s*-\s*id:\s*(\d+)\s*$")

# payload 를 읽는 두 형태 — 직접 접근, 그리고 공용 헬퍼.
READS_PAYLOAD = re.compile(r"payload\.trigger_warning|readTriggerWarning\s*\(")
# 건너뛰기 경로. 결정값이 "skip" 인 곳.
HAS_SKIP = re.compile(r'["\']skip["\']')
# 폐기된 레거시 플래그를 *코드로* 읽는 경우만 잡는다(주석의 언급은 제외).
LEGACY_FLAG = re.compile(r'["\']violence_warning["\']')


def scenes_with_warning(path: Path) -> list[int]:
    """yml 에서 trigger_warning 이 붙은 씬 id 목록.

    PyYAML 을 쓰지 않는다 — 이 잡(frontend)에는 setup-python 이 붙어 있지 않고
    러너 기본 python3 에 pyyaml 이 있다는 보장이 없다. 우리가 볼 것은 키의
    존재 여부뿐이라 줄 단위 스캔으로 충분하다.
    """
    ids: list[int] = []
    current: int | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = SCENE_ID_LINE.match(line)
        if m:
            current = int(m.group(1))
            continue
        if TRIGGER_LINE.match(line) and current is not None:
            ids.append(current)
    return ids


def main() -> int:
    if not SCENARIOS.is_dir():
        print(f"FAIL  시나리오 디렉터리가 없다: {SCENARIOS.relative_to(ROOT)}")
        return 1

    violations: list[str] = []
    notes: list[str] = []
    checked = 0

    for yml in sorted(SCENARIOS.glob("*.yml")):
        char = yml.stem
        gated = scenes_with_warning(yml)
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
    print("  ⚠️ 이 초록의 주장 범위: 화면이 payload 를 *읽는다* 까지다.")
    print("     카드가 실제로 렌더되는지·문구가 적절한지는 재지 않는다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
