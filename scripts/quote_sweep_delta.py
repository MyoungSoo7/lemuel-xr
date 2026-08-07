#!/usr/bin/env python3
"""AC-2 판정기 — `quote_sweep.py` 1군 개수가 **직전 판보다 늘었는가**.

**왜 있는가.** `SEED-RAHAB.md` §8-1 의 AC-2 는 「1군이 직전 판보다 늘지 않음」이다.
그런데 `quote_sweep.py` 는 **이번 판만** 센다. 직전 판의 수치는 여덟 판 동안 **사람이
손으로 옮겨 적었고**, seed 자신이 그 사실을 적어 두었다 — 「이 비교는 사람이 두 판의
수치를 대조해야 성립하고, 그것을 하는 도구는 없다」.

**실제로 틀렸다.** rev.11 중간 상태(`07fe3ec`)에서 1군은 **30** 이었는데 문서에는
직전 판 값 **28** 이 그대로 적혀 있었고, AC-2 는 그 힘으로 초록이었다. **판정기가 없는
AC 는 판정기가 없다는 사실 때문에 틀린다.** 이 파일이 그 자리를 맡는다.

**어떻게 재는가.** git 에서 직전 판의 문서를 꺼내 임시 파일로 쓰고, **같은 도구를 두 번
돌려** 1군 개수를 비교한다. 두 수치 모두 실행에서 나오므로 손으로 옮겨 적는 자리가 없다.
⚠️ **남는 손 옮김이 하나 있다 — 직전 판을 가리키는 git ref 자체다.** 그것은 인자로 받고,
seed §8-1 AC-2 행이 명령 전문에 적는다. 이 파일은 ref 를 추측하지 않는다.

**rc 규약**: `0` 늘지 않음 · `1` 늘었음 · `2` 판정 불가(ref 없음·도구 출력 불일치) ·
`≥126` 실행 실패.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWEEP = os.path.join(ROOT, "scripts/quote_sweep.py")
# `quote_sweep.py` 의 1군 머리글. 이 자구가 바뀌면 여기서 조용히 못 읽는 것이 아니라
# `판정 불가(2)` 로 떨어진다 — 사본을 두지 않는 대가로 결합이 하나 생기고, 그 결합은
# 끊어졌을 때 초록이 아니라 BLOCKED 를 낸다.
G1 = re.compile(r"━━\s*1군[^(]*\((\d+)\)")


def group1(path: str) -> int | None:
    out = subprocess.run([sys.executable, SWEEP, path],
                         capture_output=True, text=True, cwd=ROOT)
    m = G1.search(out.stdout)
    return int(m.group(1)) if m else None


def main() -> int:
    ap = argparse.ArgumentParser(description="1군 개수의 판간 증감 판정 (AC-2)")
    ap.add_argument("doc", nargs="?", default="docs/SEED-RAHAB.md")
    ap.add_argument("--baseline", required=True,
                    help="직전 판을 가리키는 git ref (예: rev.10 의 커밋 SHA)")
    a = ap.parse_args()

    doc = a.doc if os.path.isabs(a.doc) else os.path.join(ROOT, a.doc)
    rel = os.path.relpath(doc, ROOT)
    if not os.path.exists(doc):
        sys.stderr.write(f"문서 없음: {doc}\n")
        return 2

    show = subprocess.run(["git", "show", f"{a.baseline}:{rel}"],
                          capture_output=True, text=True, cwd=ROOT)
    if show.returncode != 0:
        sys.stderr.write(f"직전 판을 못 꺼냈다 ({a.baseline}:{rel}) — {show.stderr.strip()}\n")
        return 2

    with tempfile.TemporaryDirectory() as d:
        prev_path = os.path.join(d, os.path.basename(rel))
        open(prev_path, "w", encoding="utf-8").write(show.stdout)
        prev = group1(prev_path)
    curr = group1(doc)

    if prev is None or curr is None:
        sys.stderr.write("quote_sweep.py 출력에서 1군 머리글을 못 읽었다 — 판정 불가\n")
        return 2

    print(f"1군 개수 — 직전 판({a.baseline}) {prev} → 이번 판 {curr}")
    if curr > prev:
        print(f"  ✗ {curr - prev}건 늘었다 — AC-2 FAIL")
        print("  ⚠️ 늘어난 건은 §5-1-a 자구 대조를 통과해야 한다. 개수만 되돌리지 말 것.")
        return 1
    print(f"  ✓ 늘지 않았다 — AC-2 PASS ({'같다' if curr == prev else f'{prev - curr}건 줄었다'})")
    print("  ⚠️ 이 초록이 말하는 것은 **개수의 방향**뿐이다 — 28건 각각이 자구인지 아닌지는")
    print("     `docs/RAHAB-REVIEW-LOG.md` 가 판정할 몫이고, rev.7 이후 아무도 다시 보지 않았다.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)
