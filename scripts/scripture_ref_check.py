#!/usr/bin/env python3
"""시나리오의 `scripture_ref` 가 실제로 열리는 본문을 가리키는가 — 끊어진 포인터 검사.

## 왜 생겼나 (2026-08-20)

`docs/MVP-JOB.md` 를 쓰다가 욥 Scene 1(`job-2:13`) · Scene 3(`job-4:7`) 의 참조가
`scripture_passages` 시드에 **행이 없다** 는 것을 발견했다. 한 인물의 사고인 줄 알고
전 시나리오를 훑었더니 그게 아니었다 — 실측으로 **44개 참조 중 31개가 끊겨 있고,
예수·다윗·룻·모세는 100% 끊겨 있다.**

즉 이 검사는 새 규약을 도입하는 것이 아니라 **이미 그런 상태였다는 사실** 을 처음
기계로 세우는 것이다. 그래서 초록으로 출발하지 않는다. 빨강을 지우는 것이 목적이
아니라 **더 나빠지는 것을 막고, 고칠 때마다 줄어드는 것이 보이게** 하는 것이 목적이다
(`scripts/gates/BASELINE.json` 래칫에 걸어 둔다).

## 지금은 사용자에게 보이는 고장이 아니다 — 그런데 왜 재나

`ScenePayloadAssembler:51` 은 `scriptureRef` 문자열을 payload 에 그대로 실어 보낼 뿐
본문 테이블과 조인하지 않는다. 미션 화면(`frontend/src/app/<char>/page.tsx`) 은 씬의
`scriptureRef` 를 렌더하지 않는다 — 본문 모달(`PassageModal`) 은 `/topics` 계열
전용이다. **따라가는 사람이 아직 없는 포인터** 라서 지금은 아무도 안 아프다.

그 "아직" 이 이 검사의 존재 이유다. 미션 화면에 원문 열람을 붙이는 순간 31개 씬이
조용히 빈다. 조용히 비는 실패는 로그도 안 남긴다.

## 두 가지 다른 고장을 구별한다

1. **행 없음** — `job-4:7` 처럼 형식은 맞는데 시드에 그 행이 없다. 시드를 추가하면
   해소된다.
2. **형식상 영영 못 맞음** — `ruth-1:8,1:9,1:16` (여러 절 묶음) · `1sam-17:38-39`
   (범위) 처럼 `reference` 열의 단일 절 키(`job-42:5`) 와 **모양이 다르다.** 시드를
   아무리 넣어도 이 문자열로는 조회가 안 된다. 스키마 결정이 필요한 부채다.

책 약어 불일치도 같이 찍는다 — `jesus.yml` 은 `mt-5:3`, 시드는 `matt-6:26` 이다.
같은 마태복음이 두 약어로 존재한다.

## 이 검사가 재지 않는 것

행이 있다고 **자구가 맞는지** 는 안 본다. `translation` 이 무엇인지도 안 본다
(욥 본문은 전부 `modern` = 현대인의 성경이고, 시드 파일 스스로 "≤10명 fair use /
공개 전 라이선스 협의 or 개역개정 swap" 이라고 적어 뒀다). 조회 키가 맞느냐까지다.

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

    rows: list[tuple[str, list[str], list[str], list[str]]] = []
    for path in sorted(SCENARIOS.glob("*.yml")):
        doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        refs: list[str] = []
        walk_refs(doc, refs)
        malformed = [r for r in refs if not SINGLE_RE.match(r)]
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
    seed_books = {book_of(r) for r in seeded}
    orphan_books = sorted(b for b in scen_books if b not in seed_books)
    if orphan_books:
        print(f"  ⓘ 시드에 같은 이름의 책이 없는 약어: {', '.join(orphan_books)}")
        print("    (마태는 시나리오 `mt-` · 시드 `matt-` 로 갈려 있다 — 같은 책, 두 약어)\n")

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
    print("     자구가 맞는지도, translation 이 무엇인지도, 그 본문을 화면이")
    print("     실제로 여는지도 재지 않는다 — 지금 미션 화면은 이 값을 렌더조차")
    print("     하지 않는다. 초록이 나와도 그것은 '열 수 있다' 이지 '열린다' 가 아니다.")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
