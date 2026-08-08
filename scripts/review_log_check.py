#!/usr/bin/env python3
"""AC-2b 판정기 — `quote_sweep.py` 1군 **전건에 사람 판정 기록이 있는가**.

**왜 있는가.** `SEED-RAHAB.md` §8-1 의 AC-2b 는 「1군 전건에 사람 판정 기록이 있음」인데,
그 판정을 담을 문서(`docs/RAHAB-REVIEW-LOG.md`)가 rev.7 이후 아홉 판 동안 없었고
**그것을 재는 수단도 없어서** AC-2b 는 줄곧 `BLOCKED` 였다. BLOCKED 는 초록이 아니지만,
**아무도 세지 않는 BLOCKED 는 시간이 지나면 배경이 된다.** 이 파일이 그 자리를 맡는다.

**무엇을 재는가.**

1. **전수성** — 이번 실행의 1군 조각 전부가 로그에 있는가. 하나라도 빠지면 FAIL.
2. **잔재 없음** — 로그에만 있고 1군에는 없는 행(문서가 고쳐져 해소된 자리)을 찾아낸다.
   조용히 남으면 「28건 판정했다」는 숫자가 **판정 대상보다 커진다.**
3. **판정의 기계 검증** — 판정 등급마다 기계로 확인 가능한 단언이 다르다:

   | 등급 | 뜻                                   | 이 판정기가 실행으로 확인하는 것                       |
   | ---- | ------------------------------------ | ------------------------------------------------------ |
   | `A`  | 정본에 실재 · 정확한 인용            | 조각이 정본에 있고, `정본절`/`정본파일` 이 맞다        |
   | `B`  | 옛 **자구 훼손**을 일부러 인용       | 조각이 정본에 **없고**, `정본대체` 는 정본에 **있다**  |
   | `C`  | 성경 자구가 아님 (문서 자신의 표현)  | 조각이 정본에 **없다** — 그 이상은 못 잰다             |

🚨 **`C` 는 절반만 검사받는다.** 「정본에 없다」는 기계가 말하지만 「그래서 자구가 아니다」는
**사람만 말할 수 있다.** 이 판정기는 `C` 를 통과시키되 **몇 건이 사람 판정에만 기대고 있는지
매번 숫자로 찍는다.** 그 숫자를 0 으로 만들 방법은 없다 — 없앨 수 없는 것은 숨기지 않는다.

**rc 규약**: `0` 전건 판정 확인 · `1` 누락·잔재·판정 불일치 · `2` 판정 불가(로그 없음) ·
`≥126` 실행 실패.

    python3 scripts/review_log_check.py
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quote_sweep as qs  # noqa: E402  — 1군 정의는 저 파일 하나뿐이다

ROOT = qs.ROOT
LOG = "docs/RAHAB-REVIEW-LOG.md"
SEED = "docs/SEED-RAHAB.md"

GRADES = {"A", "B", "C"}
# 로그 행: | 01 | A | `조각` | 근거… | 판정… |
ROW = re.compile(r"^\|\s*(\d+)\s*\|\s*([ABC])\s*\|(.+?)\|(.+?)\|(.+?)\|\s*$")
BACKTICK = re.compile(r"`([^`]+)`")
FIELD = re.compile(r"(정본절|정본파일|정본대체)\s*=\s*(?:`([^`]+)`|([^\s·|]+))")


def parts(frag: str) -> list[str]:
    """`quote_sweep` 과 **같은 방식**으로 생략부호에서 쪼갠다.

    쪼개지 않으면 「여호와께서 … 홍해 물을 마르게 하신 일」이 정본에 통째로 있어야
    통과하게 되고, 정확한 부분 인용이 전부 불일치로 뜬다.
    ⚠️ `⟂` 도 쪼갠다 — `quote_sweep` 이 **미일치 조각 여럿을 그 기호로 이어 붙여**
    내놓기 때문이다. 이어 붙인 문자열은 정본 어디에도 없으므로, 쪼개지 않으면
    등급 `A` 가 원리적으로 통과할 수 없다.
    """
    return [p.strip() for p in re.split(r"…|\.\.\.|⟂", frag) if p.strip()]


def all_in(frag: str, scope: str) -> bool:
    return all(p in scope for p in parts(frag))


def fields(cell: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for m in FIELD.finditer(cell):
        out[m.group(1)] = (m.group(2) or m.group(3)).strip()
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="1군 전건 사람 판정 기록 확인 (AC-2b)")
    ap.add_argument("--seed", default=SEED)
    ap.add_argument("--log", default=LOG)
    a = ap.parse_args()

    seed_p, log_p = ROOT / a.seed, ROOT / a.log
    if not seed_p.exists():
        sys.stderr.write(f"seed 없음: {seed_p}\n")
        return 2
    if not log_p.exists():
        sys.stderr.write(f"리뷰 로그가 없다: {log_p} — 판정 불가\n")
        return 2

    r = qs.collect(seed_p)
    canon, verses = r["canon"], r["verses"]
    # 1군 조각. 같은 조각이 여러 줄에 있으면 줄 목록을 모은다 —
    # 판정은 **조각 단위**다. 줄 번호로 키를 잡으면 문서를 한 줄만 밀어도 전부 미아가 된다.
    tier_a: dict[str, list[int]] = {}
    for lineno, frag, _note in r["tier_a"]:
        tier_a.setdefault(frag, []).append(lineno)

    rows, dup = {}, []
    for line in log_p.read_text(encoding="utf-8").splitlines():
        m = ROW.match(line)
        if not m:
            continue
        bt = BACKTICK.search(m.group(3))
        if not bt:
            continue
        frag = qs.normalize(bt.group(1))
        if frag in rows:
            dup.append(frag)
        rows[frag] = dict(no=m.group(1), grade=m.group(2),
                          ground=m.group(4), verdict=m.group(5).strip())

    problems: list[str] = []
    for frag in dup:
        problems.append(f"로그에 같은 조각이 두 번: {frag[:60]}")

    missing = [f for f in tier_a if f not in rows]
    stale = [f for f in rows if f not in tier_a]
    for f in missing:
        problems.append(f"판정 없음 — SEED:{tier_a[f]} {f[:70]}")
    for f in stale:
        problems.append(f"잔재 행 — 이번 1군에 없는 조각을 판정하고 있다: {f[:70]}")

    human_only, by_grade = [], {"A": 0, "B": 0, "C": 0}
    for frag, e in rows.items():
        if frag in stale:
            continue
        g = e["grade"]
        by_grade[g] += 1
        fl = fields(e["ground"])
        in_canon = all_in(frag, canon)

        if g == "A":
            if not in_canon:
                problems.append(f"[A] 정본에 없다: {frag[:60]}")
                continue
            ref = fl.get("정본절")
            if ref:
                if ref not in verses:
                    problems.append(f"[A] 정본절 표기가 정본 표에 없다 ({ref}): {frag[:50]}")
                elif not all_in(frag, verses[ref]):
                    problems.append(f"[A] {ref} 본문에 그 조각이 없다: {frag[:50]}")
            elif "정본파일" in fl:
                p = ROOT / fl["정본파일"]
                if not p.exists():
                    problems.append(f"[A] 정본파일 없음 ({fl['정본파일']}): {frag[:50]}")
                elif not all_in(frag, qs.normalize(p.read_text(encoding="utf-8"))):
                    problems.append(f"[A] {fl['정본파일']} 에 그 조각이 없다: {frag[:50]}")
            else:
                problems.append(f"[A] 정본절= 또는 정본파일= 이 없다: {frag[:50]}")

        elif g == "B":
            if in_canon:
                problems.append(f"[B] 훼손이라 했는데 정본에 그대로 있다: {frag[:60]}")
            alt = fl.get("정본대체")
            if not alt:
                problems.append(f"[B] 정본대체= 가 없다: {frag[:60]}")
            elif not all_in(qs.normalize(alt), canon):
                problems.append(f"[B] 정본대체가 정본에 없다 ({alt[:40]}): {frag[:40]}")
            else:
                ref = fl.get("정본절")
                if ref and ref in verses and not all_in(qs.normalize(alt), verses[ref]):
                    problems.append(f"[B] 정본대체가 {ref} 본문에 없다: {alt[:50]}")

        else:  # C
            if in_canon:
                problems.append(f"[C] 자구가 아니라 했는데 정본에 있다: {frag[:60]}")
            else:
                human_only.append(frag)

        if not e["verdict"] or e["verdict"] in {"-", "—"}:
            problems.append(f"[{g}] 판정 사유가 비어 있다: {frag[:60]}")

    n_occ = len(r["tier_a"])
    n1, nlog = len(tier_a), len(rows) - len(stale)
    # 출현 수와 고유 조각 수를 **둘 다** 찍는다. 같은 조각이 두 줄에 있으면
    # `quote_sweep` 은 2 로 세고 판정은 1 건이면 족하다 — 한쪽만 찍으면
    # 「28건인데 27건만 판정했다」로 읽히거나 그 반대로 부풀려 읽힌다.
    print(f"1군 출현 {n_occ}건 · 고유 조각 {n1}개 · "
          f"로그 판정 {nlog}건 · 누락 {len(missing)} · 잔재 {len(stale)}")
    print(f"등급 — A(정본 실재·정확) {by_grade['A']} · "
          f"B(옛 훼손 인용) {by_grade['B']} · C(자구 아님) {by_grade['C']}")

    if problems:
        print(f"\n✗ 불일치 {len(problems)}건")
        for p in problems:
            print(f"  ✗ {p}")
        return 1

    print("\n✓ 1군 전건에 판정 기록이 있고, 기계로 확인 가능한 단언은 전부 실행으로 맞췄다.")
    print(f"⚠️ 그중 {len(human_only)}건(등급 C)은 **「정본에 없다」까지만 기계가 확인했다.**")
    print("   「그래서 자구가 아니라 문서 자신의 표현이다」는 사람 판정이고, 이 판정기는")
    print("   그것을 재지 못한다. 이 숫자는 0 이 될 수 없다 — 줄어들면 그건 문서가")
    print("   자기 표현을 지운 것이지 검사가 강해진 것이 아니다.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)
