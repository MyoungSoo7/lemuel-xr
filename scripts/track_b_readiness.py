#!/usr/bin/env python3
"""
트랙 B 인물 파이프라인 — **단계 역전**을 잰다.

왜 만들었나 (2026-08-11):
    `docs/PLAN.md` 표는 인물 16명, `content/` 저작은 11명, 런타임 `Character` enum 은
    7명이다. 그런데 두 집합이 포함관계가 아니다 — 겹치는 건 5명뿐이고, 저작이 다 끝난
    인물 6명은 enum 에 없어서 못 놀고, 반대로 **저작 없이 런타임에만 있는 인물**도 있다.

    이 어긋남을 여태 아무 스크립트도 재지 않았다. 사람이 디렉터리를 세어서 발견했고,
    그 사이 두 판의 문서가 낡은 현황을 들고 있었다. 세지 않으면 또 어긋난다.

무엇을 FAIL 로 보나:
    **역전** 하나만 본다 — 뒤 단계가 있는데 앞 단계가 없는 경우.
    사용자에게 나가는 쪽(런타임)이 저작·설계보다 앞서 있다는 뜻이고, 그건 게이트도
    검토 기록도 없는 것이 노출 경로에 올라와 있다는 뜻이다.

    반대로 앞 단계만 있고 뒤가 없는 것(저작은 끝났는데 아직 못 논다)은 FAIL 이 아니다.
    그건 미완이지 위험이 아니다 — 세어서 보여 주기만 한다.

역전을 과대 해석하지 말 것 (2026-08-11 교정):
    이 스크립트는 *디렉터리에 파일이 있는지* 만 본다. 그래서 "앞 단계가 없다" 를
    "안전 통제가 없다" 로 읽으면 틀린다 — 실제로 한 번 틀렸다. job 은 설계·게이트·저작
    디렉터리가 전부 비었지만, 런타임 시나리오 파일 *안에* safety_gates 1개와 금칙 토큰
    35종, 트리거 경고·동의 카드·스킵 경로를 직접 들고 있다. 통제가 없던 게 아니라
    위치가 달랐다.
    그래서 역전 사유 옆에 **자체 통제 보유 여부** 를 같이 찍는다. 통제가 없는 역전
    (`자체통제 없음`)이 통제를 다른 위치에 둔 역전보다 훨씬 나쁘다.
    단, 자체 통제가 있어도 역전은 역전이다 — 설계 문서·게이트 스위트·검토 기록이
    없다는 사실 자체는 그대로다.

이 초록이 말하지 않는 것:
    ① 산출물의 품질 — 파일이 있는지만 본다. 안에 뭐가 적혔는지는 안 읽는다.
    ② 사람 사인오프 — `docs/CONTENT-WORKFLOW.md` 는 신학·심리 검토자의 승인을
       프로덕션 반영 조건으로 둔다. 이 스크립트는 그 승인 여부를 재지 않는다.
       파일이 다 있어도 그것은 "놀 수 있다"이지 "내보내도 된다"가 아니다.
    ③ 설계표(PLAN.md)에 있는데 아직 아무 산출물도 없는 인물 — 세지 못한다.
       이 스크립트의 인물 목록은 *파일에서* 모으기 때문이다.

rc: 0=역전 없음 · 1=역전 있음 · 2=판정 불가(인물 목록을 못 만듦)
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# (키, 표시명) — 순서가 곧 파이프라인 순서다. 뒤가 있는데 앞이 없으면 역전이다.
STAGES = [
    ("design", "설계"),
    ("gates", "게이트"),
    ("authoring", "저작"),
    ("runtime_content", "런타임대본"),
    ("scenario", "시나리오"),
    ("enum", "런타임"),
]


def enum_values() -> set[str]:
    src = (ROOT / "backend/src/main/kotlin/github/lms/lemuel/xr/game/domain/Character.kt").read_text(
        encoding="utf-8"
    )
    body = src.split("enum class Character", 1)[-1]
    return set(re.findall(r'^\s*[A-Z_]+\("([a-z_]+)"\)', body, re.M))


def self_controls() -> dict[str, tuple[int, int]]:
    """런타임 시나리오 파일이 *스스로* 들고 있는 (safety_gates 수, 금칙 토큰 수).

    앞 단계 디렉터리가 비어도 여기 값이 있으면 그 인물은 무방비가 아니다.
    파싱 실패는 0 이 아니라 판정 불가로 올린다 — 못 읽은 것을 '없음' 으로 세면
    이 스크립트가 바로 그 오경보를 다시 낸다.
    """
    import yaml  # 이 함수에서만 필요하다. 나머지 판정은 표준 라이브러리로 충분하다.

    out: dict[str, tuple[int, int]] = {}
    for p in sorted((ROOT / "backend/src/main/resources/scenarios").glob("*.yml")):
        doc = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        gates = [g for g in (doc.get("safety_gates") or []) if isinstance(g, dict)]
        tokens = sum(len(g.get("lint_forbidden_tokens") or []) for g in gates)
        out[p.stem] = (len(gates), tokens)
    return out


def collect() -> dict[str, dict[str, bool]]:
    stages: dict[str, set[str]] = {
        "design": {
            p.stem.removeprefix("MVP-").lower()
            for p in (ROOT / "docs").glob("MVP-*.md")
            if not p.stem.endswith("-CONTENT")
        },
        "runtime_content": {
            p.stem.removeprefix("MVP-").removesuffix("-CONTENT").lower()
            for p in (ROOT / "docs").glob("MVP-*-CONTENT.md")
        },
        "gates": {p.stem for p in (ROOT / "scripts/gates").glob("*.yml")},
        "authoring": {p.name for p in (ROOT / "content").iterdir() if p.is_dir()},
        "scenario": {p.stem for p in (ROOT / "backend/src/main/resources/scenarios").glob("*.yml")},
        "enum": enum_values(),
    }
    universe = sorted(set().union(*stages.values()))
    return {c: {k: c in v for k, v in stages.items()} for c in universe}


def main() -> int:
    try:
        table = collect()
        controls = self_controls()
    except (OSError, KeyError, ImportError) as exc:
        print(f"[BLOCKED] 인물 목록을 만들지 못했다 — {exc}")
        return 2
    except Exception as exc:  # yaml 파싱 실패 등 — 못 읽은 것을 '없음' 으로 세지 않는다
        print(f"[BLOCKED] 런타임 시나리오의 자체 통제를 읽지 못했다 — {exc}")
        return 2

    if not table:
        print("[BLOCKED] 인물이 하나도 없다 — 순회 0회는 통과가 아니다.")
        return 2

    keys = [k for k, _ in STAGES]
    width = max(len(c) for c in table)
    header = "  ".join(label for _, label in STAGES)
    print(f"{'인물'.ljust(width)}  {header}")
    print("-" * (width + len(header) + 2))

    label_of = dict(STAGES)
    inverted: dict[str, list[str]] = {}
    playable: list[str] = []
    stalled: list[str] = []

    for name, has in sorted(table.items()):
        marks = []
        for k, lbl in STAGES:
            marks.append(("O" if has[k] else "·").center(len(lbl)))
        print(f"{name.ljust(width)}  " + "  ".join(marks))

        # 뒤 단계가 하나라도 있으면, 그보다 앞의 빠진 단계는 전부 역전이다.
        last = max((i for i, k in enumerate(keys) if has[k]), default=-1)
        missing = [k for k in keys[:last] if not has[k]]
        if missing:
            inverted[name] = missing
        if has["enum"] and has["scenario"]:
            playable.append(name)
        elif has["authoring"]:
            stalled.append(name)

    print()
    print(f"놀 수 있음 {len(playable)}: {', '.join(playable)}")
    print(f"저작은 끝났으나 못 놂 {len(stalled)}: {', '.join(stalled) or '없음'}")
    print()

    for name, has in sorted(table.items()):
        if name in inverted:
            gone = " · ".join(label_of[k] for k in inverted[name])
            n_gates, n_tokens = controls.get(name, (0, 0))
            own = (
                f"자체통제 게이트 {n_gates}·토큰 {n_tokens}"
                if n_gates
                else "자체통제 없음"
            )
            print(f"  [FAIL   ] {name} — 런타임에 올라와 있는데 앞 단계가 없다: {gone} ({own})")
        else:
            print(f"  [PASS   ] {name}")

    n_fail = len(inverted)
    print(f"\n--- PASS {len(table) - n_fail} / FAIL {n_fail} / BLOCKED 0 ---")
    print(f"  (인물 {len(table)} · 단계 역전 {n_fail})")
    print("  ⚠️ 이 검사의 주장 범위: '뒤 단계가 앞 단계보다 앞서 있지 않다' 까지다.")
    print("     산출물의 품질도, 신학·심리 검토자의 사인오프도 재지 않는다 —")
    print("     전부 O 라도 그것은 '놀 수 있다'이지 '내보내도 된다'가 아니다.")
    return 1 if inverted else 0


if __name__ == "__main__":
    sys.exit(main())
