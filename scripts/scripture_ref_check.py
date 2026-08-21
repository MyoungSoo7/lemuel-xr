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

## content/ 축 — 같은 키 이름이 두 문법을 갖고 있었다 (2026-08-22)

위의 모든 이야기는 `backend/.../scenarios/*.yml` 이야기다. 그 옆에 `content/**/*.yml`
가 있고, 거기에도 `scripture_ref` 키가 산다 — **다른 문법으로.** 실측:
시나리오 50건은 전부 기계 키(`ex-3:5`), `content/peter/scene2.yml` 3건은 한글 라벨
(`"누가복음 22:57"`) 이었다. content/ 는 클래스패스가 아니라(`newchar_gates.py:1282`)
지금 아픈 사람은 없다. 아픈 것은 베드로를 시나리오로 옮기는 사람이 그 세 줄을
그대로 복사하는 순간이고, 그때 이 검사기는 시나리오만 보므로 아무 말도 안 한다.

그래서 content 축이 셋을 잰다.

1. `content/**/*.yml` 의 `scripture_ref` 는 기계 키여야 한다. 사람이 읽는 라벨은
   `verse_ref` 로 적는다 — `content/ruth/*.yml` 이 이미 17건 그렇게 쓴다.
2. `theology_footer_refs.scripture_refs[].translation` 은 **살아 있는 코드** 여야 한다
   (`rev`·`modern`, 정본은 `V1__init_schema.sql:46`). 2026-08-22 이전 59건은 전부
   `krv` 였고 그 값을 가진 행은 시드에 0건이다. 조회되지 않는 라벨은 근거를
   확인하려는 검토자를 조용히 헛걸음시킨다.
3. 같은 블록의 `anchor_block` 은 그 파일 안에서 **구조로** 찾아져야 한다. 점 경로
   (`ix_s4_choose_response.options.C`)와 키 이름(`ai_optout_branch`)도 통과시킨다 —
   주석의 목적은 사람이 찾아가는 것이지 id 규격을 맞추는 게 아니다.

`ref` 문자열 자체는 §8 문법(정식 책이름 + 범위 허용)으로만 본다. 이 블록은 조회 키가
아니라서 단일 절로 펼 수 없다 — 근거 본문은 원래 `출애굽기 3:11~4:13` 처럼 한 절이
아닌 경우가 많다. 규약은 `docs/SCRIPTURE-REF-CONVENTION.md` §8.

## 이 검사가 재지 않는 것

행이 있다고 **자구가 맞는지** 는 안 본다 — 그건 `scripts/scripture_text_check.py`
가 잰다. content 축의 `translation` 도 **코드가 살아 있는지** 만 보지 그 본문이 정말
그 역본인지는 안 본다. `anchor_block` 의 점 경로는 마디가 전부 그 파일에 있는지만
보지 **그 경로를 따라가면 나오는지** 는 안 본다 — `ix_s4.options.C` 와 `ix_s9.options.C`
를 구별하지 못한다. 조회 키가 맞느냐까지다.

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

CONTENT = ROOT / "content"

# 살아 있는 역본 코드. 정본은 `V1__init_schema.sql:46` 의 DDL 주석이다 —
# 문서(`docs/DB-SCHEMA.md`)가 아니라 DDL 이 정본인 이유는 2026-08-22 에
# 그 문서가 6개월간 없는 코드(`krv`·`nlt-ko`)를 적고 있었기 때문이다.
TRANSLATIONS = frozenset({"rev", "modern"})

# §8 검토자 주석 문법 — 정식 책이름 + 절 표기(범위·장 넘김·묶음) + 선택적 괄호 주기.
ANNOT_RE = re.compile(
    r"^(?P<book>[가-힣]+)\s+"
    r"\d+:\d+(?:[~-]\d+(?::\d+)?)?(?:;\s*\d+(?::\d+)?(?:[~-]\d+)?)*"
    r"(?:\s*\([^)]+\))?$"
)


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


def names_in(node: object, out: set[str]) -> None:
    """파일 안에서 anchor 가 가리킬 수 있는 이름 — `id` 값과 키 이름 전부."""
    if isinstance(node, dict):
        for key, val in node.items():
            out.add(str(key))
            if key == "id" and isinstance(val, str):
                out.add(val)
            names_in(val, out)
    elif isinstance(node, list):
        for item in node:
            names_in(item, out)


def anchor_resolves(anchor: str, names: set[str]) -> bool:
    """통짜 이름이거나, 점 경로의 **모든 마디** 가 파일 안에 있으면 통과."""
    if anchor in names:
        return True
    parts = anchor.split(".")
    return len(parts) > 1 and all(part in names for part in parts)


def all_anchors(node: object, out: list[str]) -> None:
    """`anchor_block` 값 전부. scripture_refs 안쪽만 보지 않는다 — 같은 주장을 하는
    키를 내가 아는 것만 골라 재면 모르는 자리에서 조용히 끊긴다(E3 축의 교훈)."""
    if isinstance(node, dict):
        for key, val in node.items():
            if key == "anchor_block" and isinstance(val, str):
                out.append(val)
            all_anchors(val, out)
    elif isinstance(node, list):
        for item in node:
            all_anchors(item, out)


def annotation_refs(doc: object) -> list[dict]:
    out: list[dict] = []
    for entry in (doc.get("theology_footer_refs") or []) if isinstance(doc, dict) else []:
        if isinstance(entry, dict):
            out.extend(x for x in (entry.get("scripture_refs") or []) if isinstance(x, dict))
    return out


def check_content(full_books: set[str]) -> tuple[int, int, int, list[str]]:
    """content/ 축. (`scripture_ref` 수, 주석 수, anchor 수, 실패 줄)."""
    fails: list[str] = []
    n_key = n_annot = n_anchor = 0
    for path in sorted(CONTENT.glob("**/*.yml")):
        doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        rel = path.relative_to(ROOT)

        # ① `scripture_ref` 키 이름은 기계 키 전용이다 (규약 §8-1).
        refs: list[str] = []
        walk_refs(doc, refs)
        for ref in refs:
            n_key += 1
            if not SINGLE_RE.match(ref):
                fails.append(
                    f"  [FAIL   ] {rel} — scripture_ref: {ref!r} 는 기계 키가 아니다"
                    f" — 사람이 읽는 라벨은 verse_ref 로 적는다 (규약 §8-1)"
                )

        # ③ anchor_block 은 그 파일 안에서 구조로 찾아져야 한다 — 전수.
        anchors: list[str] = []
        all_anchors(doc, anchors)
        names: set[str] = set()
        if anchors:
            names_in(doc, names)
        for anchor in anchors:
            n_anchor += 1
            if not anchor_resolves(anchor, names):
                fails.append(
                    f"  [FAIL   ] {rel} — anchor_block: {anchor!r} 를 이 파일에서 못 찾는다"
                )

        # ② 검토자 주석 블록의 문법·역본 코드.
        for sr in annotation_refs(doc):
            n_annot += 1
            ref = str(sr.get("ref", ""))
            tr = str(sr.get("translation", ""))
            if not ANNOT_RE.match(ref) or ANNOT_RE.match(ref).group("book") not in full_books:
                fails.append(
                    f"  [FAIL   ] {rel} — ref: {ref!r} 가 §8 문법이 아니다"
                    f" — <정식 책이름> <장:절>[범위·묶음][ (주기)]"
                )
            if tr not in TRANSLATIONS:
                fails.append(
                    f"  [FAIL   ] {rel} — translation: {tr!r} 는 없는 코드다"
                    f" — {' · '.join(sorted(TRANSLATIONS))} (정본 V1__init_schema.sql:46)"
                )
    return n_key, n_annot, n_anchor, fails


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

    # ── content/ 축 ──────────────────────────────────────────────
    try:
        sys.path.insert(0, str(ROOT / "scripts"))
        from check_monologue_quotes import BOOK_ALIAS
    except Exception as exc:  # noqa: BLE001 — 판정 불가는 실패가 아니다
        print(f"\n[BLOCKED] 책이름 정본(check_monologue_quotes.BOOK_ALIAS)을 못 읽었다 — {exc}")
        return 126

    n_key, n_annot, n_anchor, content_fails = check_content(set(BOOK_ALIAS))
    print(
        f"\ncontent/ 축 — scripture_ref {n_key}건 · 검토자 주석 {n_annot}건"
        f" · anchor_block {n_anchor}건"
    )
    if content_fails:
        print("\n".join(content_fails))
    else:
        print(f"  [PASS   ] content — 키 문법 · 역본 코드 · anchor_block 전부 성립")
    fails += len(content_fails)

    passes = len(rows) - (fails - len(content_fails))
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
