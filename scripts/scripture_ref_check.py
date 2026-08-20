#!/usr/bin/env python3
"""시나리오의 `scripture_ref` 가 실제로 열리는 본문을 가리키는가 — 끊어진 포인터 검사.

## 왜 생겼나 (2026-08-20)

`docs/MVP-JOB.md` 를 쓰다가 욥 Scene 1(`job-2:13`) · Scene 3(`job-4:7`) 의 참조가
`scripture_passages` 시드에 **행이 없다** 는 것을 발견했다. 한 인물의 사고인 줄 알고
전 시나리오를 훑었더니 그게 아니었다 — 실측으로 **44개 참조 중 31개가 끊겨 있고,
예수·다윗·룻·모세는 100% 끊겨 있다.**

즉 이 검사는 새 규약을 도입한 것이 아니라 **이미 그런 상태였다는 사실** 을 처음
기계로 센 것이다. 그래서 초록으로 출발하지 않았다.

같은 날 그 31건을 갚았다 — 참조 문법을 `docs/SCRIPTURE-REF-CONVENTION.md` 로 정하고,
시나리오의 범위·묶음 참조 8건을 절 단위로 펴고, 미시드 행 46개를
`V20260820051500__seed_scenario_scripture_refs.sql` 로 채웠다. 지금은 참조 60개가
전부 열린다. 이제 이 검사의 일은 **다시 나빠지지 않게 잡아 두는 것** 이다.

## 지금은 사용자에게 보이는 고장이 아니다 — 그런데 왜 재나

`ScenePayloadAssembler:51` 은 `scriptureRef` 문자열을 payload 에 그대로 실어 보낼 뿐
본문 테이블과 조인하지 않는다. 미션 화면(`frontend/src/app/<char>/page.tsx`) 은 씬의
`scriptureRef` 를 렌더하지 않는다 — 본문 모달(`PassageModal`) 은 `/topics` 계열
전용이다. **따라가는 사람이 아직 없는 포인터** 라서 지금은 아무도 안 아프다.

그 "아직" 이 이 검사의 존재 이유다. 미션 화면에 원문 열람을 붙이는 순간 31개 씬이
조용히 빈다. 조용히 비는 실패는 로그도 안 남긴다.

## 두 가지 다른 고장을 구별한다

1. **행 없음** — `job-4:7` 처럼 형식은 맞는데 시드에 그 행이 없다.
2. **형식불가** — `ruth-1:8,1:9,1:16` (쉼표 묶음) · `1sam-17:38-39` (범위) ·
   `ruth-1:21a` (반절) 처럼 `reference` 열의 단일 절 키와 **모양이 다르다.** 시드를
   아무리 넣어도 이 문자열로는 조회가 안 된다.

책 약어가 갈리는 것도 형식불가로 친다 — `jesus.yml` 이 `mt-5:3`, 시드가 `matt-6:26`
이던 시절이 있었다. 같은 책이 두 약어로 살면 한쪽은 영원히 안 열린다. 단, 시드에
그 책이 아예 없으면(예: 시드에 `lk` 행이 하나도 없을 때) 갈렸는지 알 도리가 없으므로
잡지 않는다 — 그건 1번(행 없음)으로 잡힌다.

## 이 검사가 재지 않는 것

행이 있다고 **자구가 맞는지** 는 안 본다 — 그건 `scripts/scripture_text_check.py`
가 잰다. `translation` 라벨이 데이터와 맞는지도 안 본다. 조회 키가 맞느냐까지다.

grep 금지 — `scripts/newchar_gates.py:20`.

rc: 0=PASS · 1=FAIL · ≥126 은 판정이 아니라 실행 실패.
"""

from __future__ import annotations

import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCENARIOS = ROOT / "backend/src/main/resources/scenarios"
MIGRATIONS = ROOT / "backend/src/main/resources/db/migration"

# 시드 INSERT 의 첫 열이 `reference` 다. 줄머리에서만 딴다 — 주석·롤백 설명에 적힌
# 참조를 실재 행으로 세면 있지도 않은 행을 있다고 보고하게 된다.
SEEDED_RE = re.compile(r"^\('([A-Za-z0-9]+-\d+:\d+)'", re.M)

# 단일 절 키 모양. `reference` 열이 실제로 쓰는 형태다.
SINGLE_RE = re.compile(r"^[a-z0-9]+-\d+:\d+$")


def seeded_refs() -> set[str]:
    out: set[str] = set()
    for sql in sorted(MIGRATIONS.glob("*.sql")):
        out.update(SEEDED_RE.findall(sql.read_text(encoding="utf-8")))
    return out


def walk_refs(node: object, out: list[str]) -> None:
    """씬 트리에서 참조 문자열만 모은다. 키 이름으로 딴다 — 값 전수 검색이 아니다."""
    if isinstance(node, dict):
        for key, val in node.items():
            if key == "scripture_ref" and isinstance(val, str):
                out.append(val.strip())
            elif key == "additional_refs" and isinstance(val, list):
                out.extend(str(v).strip() for v in val)
            else:
                walk_refs(val, out)
    elif isinstance(node, list):
        for item in node:
            walk_refs(item, out)


def book_of(ref: str) -> str:
    return ref.split("-", 1)[0] if "-" in ref else ref


def main() -> int:
    seeded = seeded_refs()
    if not seeded:
        print("[BLOCKED] 시드 SQL 에서 참조를 하나도 못 읽었다 — 판정 불가")
        return 126

    seed_books = {book_of(r) for r in seeded}

    rows: list[tuple[str, list[str], list[str], list[str]]] = []
    for path in sorted(SCENARIOS.glob("*.yml")):
        doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        refs: list[str] = []
        walk_refs(doc, refs)
        malformed = [r for r in refs if not SINGLE_RE.match(r)]
        # 같은 책이 두 약어로 사는 것을 막는다 — `mt` 와 `matt` 가 그랬다.
        # 시드에 그 책이 아예 없으면 판단 근거가 없으므로 여기서는 잡지 않는다.
        malformed += [
            r for r in refs
            if r not in malformed and book_of(r) not in seed_books
            and any(b.startswith(book_of(r)) or book_of(r).startswith(b) for b in seed_books)
        ]
        missing = [r for r in refs if r not in seeded and r not in malformed]
        rows.append((path.stem, refs, missing, malformed))

    print(f"시드 행 {len(seeded)}개 · 시나리오 {len(rows)}편\n")
    print("인물       참조  해소  행없음  형식불가")
    print("-" * 44)
    for name, refs, missing, malformed in rows:
        ok = len(refs) - len(missing) - len(malformed)
        print(f"{name:9s} {len(refs):4d} {ok:5d} {len(missing):7d} {len(malformed):9d}")

    total = sum(len(r[1]) for r in rows)
    broken = sum(len(r[2]) + len(r[3]) for r in rows)
    print(f"\n참조 {total}개 중 열리지 않는 것 {broken}개\n")

    # 책 약어 불일치 — 같은 책이 두 이름으로 사는가.
    scen_books = {book_of(r) for _, refs, _, _ in rows for r in refs}
    orphan_books = sorted(b for b in scen_books if b not in seed_books)
    if orphan_books:
        print(f"  ⓘ 시드에 같은 이름의 책이 없는 약어: {', '.join(orphan_books)}\n")

    fails = 0
    for name, refs, missing, malformed in rows:
        if not missing and not malformed:
            print(f"  [PASS   ] {name} — 참조 {len(refs)}개 전부 시드에 실재")
            continue
        fails += 1
        parts = []
        if missing:
            parts.append(f"행없음 {len(missing)}: {' '.join(missing)}")
        if malformed:
            parts.append(f"형식불가 {len(malformed)}: {' '.join(malformed)}")
        print(f"  [FAIL   ] {name} — 열리지 않는 참조 — {' · '.join(parts)}")

    passes = len(rows) - fails
    print(f"\n--- PASS {passes} / FAIL {fails} / BLOCKED 0 ---")
    print("  ⚠️ 이 검사의 주장 범위: 조회 키가 시드 행과 맞느냐 까지다.")
    print("     자구가 맞는지도(scripture_text_check.py), 그 본문을 화면이 실제로")
    print("     여는지도(mission_passage_check.py + mission-passage.test.tsx) 여기서는")
    print("     재지 않는다. 초록이 나와도 그것은 '열 수 있다' 이지 '열린다' 가 아니다.")
    print("     ─ 2026-08-20 까지 미션 화면은 이 값을 렌더조차 하지 않았고, 그동안")
    print("       이 검사는 8인물 PASS 였다. 그 거리를 잰 러너가 그때 생겼다.")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
