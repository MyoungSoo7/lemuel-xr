#!/usr/bin/env python3
"""`review_log_check.py` 자신을 검사한다 — 틀린 로그를 넣으면 잡는가.

**왜 있는가.** 판정기가 초록을 낸다는 것과, 틀린 입력을 넣었을 때 빨강을 낸다는 것은
다르다. rev.11 이 「문서 층위 14/14 전건 검출」을 적어 놓고 실행 근거가 다섯뿐이었던 일이
바로 그 차이에서 나왔다. **그래서 이 파일은 처음부터 리포에 둔다** — 한 번 돌리고 버리는
스크립트로 두면 다음 판이 「검사기를 검사했다」를 근거 없이 물려받는다.

각 변이는 **판정기가 재겠다고 선언한 축 하나씩**을 무너뜨린다. 전건에서 rc=1 이 나와야
한다. 하나라도 rc=0 이면 그 축은 **선언만 있고 집행이 없는 것**이다.

🚨 **rc 만 보면 부족하다.** 처음 판(rev.11)은 변이마다 `rc=1` 만 확인했는데, 그러면
**엉뚱한 축이 대신 걸려도 통과**한다 — 아홉 축이 다 같은 `1` 을 내므로 rc 는 어느 축이
집행됐는지 구별하지 못한다. `mutate_rahab_captions.py` 를 쓰면서 이 구멍이 드러나 여기도
같은 규율로 올린다: 변이마다 **그 축이 내야 할 불일치 문구가 실제로 출력에 있는지**까지 본다.
(`review_log_check.py` 는 축 키를 안 찍고 문구만 찍는다. 그래서 문구로 맞춘다 —
문구가 바뀌면 이 파일이 먼저 빨강이 된다. 그것이 의도다.)

    python3 scripts/mutate_review_log.py
"""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG = ROOT / "docs/RAHAB-REVIEW-LOG.md"
CHECK = ROOT / "scripts/review_log_check.py"

SRC = LOG.read_text(encoding="utf-8")


def row(grade: str, must_have: str = "") -> str:
    """등급이 `grade` 이고 `must_have` 를 담은 첫 행을 통째로 돌려준다.

    ⚠️ `must_have` 가 필요한 이유가 실측으로 드러났다 — 등급 `A` 의 첫 행은
    `정본파일` 을 쓰는 행이라, 거기서 `정본절=` 을 바꾸려 하면 **아무것도 바뀌지
    않은 변이**가 만들어진다. 원본과 같은 입력은 당연히 rc=0 이고, 그것을
    「판정기가 못 잡았다」로 읽으면 **없는 구멍을 보고하게 된다.**
    """
    for m in re.finditer(rf"^\|\s*\d+\s*\|\s*{grade}\s*\|.+$", SRC, re.M):
        if must_have in m.group(0):
            return m.group(0)
    raise AssertionError(f"등급 {grade} · {must_have!r} 를 담은 행을 못 찾았다")


def regrade(rowtext: str, new: str) -> str:
    """행의 **등급 칸**만 바꾼다.

    🚨 `"| C |"` 같은 리터럴로 바꾸면 안 된다 — 마크다운 포매터가 표를 정렬하면
    칸이 `| C    |` 로 패딩되고, 그 순간 치환이 **아무것도 안 바꾼다.** 실제로
    rev.12 에서 그렇게 났고, 이 파일은 무효 변이를 AssertionError 로 세우도록
    되어 있어 rc=126 으로 멈췄다. 조용히 통과하지 않은 것이 이 하네스의 값이다.
    """
    out, n = re.subn(r"^(\|\s*\d+\s*\|\s*)[ABC](\s*\|)", rf"\g<1>{new}\g<2>",
                     rowtext, count=1)
    assert n == 1, f"등급 칸을 못 찾았다: {rowtext[:40]!r}"
    return out


A, B, C = row("A"), row("B"), row("C")
A_VERSE = row("A", "정본절=")

MUTANTS: list[tuple[str, str, str, str, str]] = [
    # (무너뜨리는 축, 그 축이 내야 할 불일치 문구(정규식), 설명, before, after)
    ("전수성", r"판정 없음 —", "판정 행 하나를 통째로 지운다", C, ""),
    ("잔재", r"잔재 행 —", "1군에 없는 조각을 판정한 행을 더한다", C,
     C + "\n| 99 | C | `이 문자열은 seed 어디에도 없다` | 정본 어디에도 없음 | 잔재 시험 |"),
    ("중복", r"같은 조각이 두 번", "같은 조각을 두 번 판정한다", C, C + "\n" + C),
    ("A·정본실재", r"\[A\] 정본에 없다", "정본에 없는 조각을 A 로 올린다",
     C, regrade(C, "A")),
    ("A·정본절", r"\[A\] (정본절 표기가 정본 표에 없다|.+ 본문에 그 조각이 없다)",
     "A 행의 정본절을 엉뚱한 절로 바꾼다",
     A_VERSE, re.sub(r"정본절=`[^`]+`", "정본절=`수 2:1`", A_VERSE, count=1)),
    ("A·근거부재", r"\[A\] 정본절= 또는 정본파일= 이 없다", "A 행에서 정본절·정본파일 을 둘 다 뺀다",
     A, re.sub(r"(정본절|정본파일)=`[^`]+`", "근거 없음", A, count=1)),
    ("B·훼손전제", r"\[B\] 훼손이라 했는데 정본에 그대로 있다", "정본에 실재하는 A 조각을 B 로 내린다",
     A, regrade(A, "B")),
    ("B·정본대체", r"\[B\] 정본대체가 정본에 없다", "B 행의 정본대체를 정본에 없는 문자열로 바꾼다",
     B, re.sub(r"정본대체=`[^`]+`", "정본대체=`있지도 않은 정본 자구`", B, count=1)),
    ("판정사유", r"판정 사유가 비어 있다", "사람 판정 칸을 비운다", C,
     re.sub(r"\|\s*[^|]+\|\s*$", "| — |", C, count=1)),
]


def run(path: Path) -> tuple[int, str]:
    # 판정기는 ROOT 기준 경로를 받는다. 임시 파일을 리포 안에 두지 않으려고 절대경로로 넘긴다.
    p = subprocess.run([sys.executable, str(CHECK), "--log", str(path)],
                       capture_output=True, text=True, cwd=ROOT)
    return p.returncode, p.stdout


def main() -> int:
    base, _ = run(LOG)
    print(f"기준선: rc={base}" + ("" if base == 0 else "  ⚠️ 기준선이 초록이 아니다"))
    print()

    caught = 0
    with tempfile.TemporaryDirectory() as d:
        docs = Path(d) / "docs"
        docs.mkdir()
        for axis, want, why, before, after in MUTANTS:
            assert SRC.count(before) == 1, (axis, SRC.count(before))
            # 🚨 변이가 원본과 같으면 그것은 검사가 아니라 **아무것도 아닌 실행**이다.
            # 실제로 한 번 이 형태가 나왔고, 하마터면 없는 구멍을 보고할 뻔했다.
            assert after != before, f"{axis}: 변이가 원본과 같다 — 무효 변이"
            p = docs / "MUTANT-LOG.md"
            p.write_text(SRC.replace(before, after), encoding="utf-8")
            rc, out = run(p)
            # rc 와 **문구**를 함께 본다. rc 만 보면 엉뚱한 축이 대신 걸려도 통과한다.
            ok = rc == 1 and re.search(want, out) is not None
            caught += ok
            print(f"{'✅' if ok else '❌'} {axis:12s} rc={rc}  — {why}")
            if not ok:
                hits = [ln.strip() for ln in out.splitlines() if ln.lstrip().startswith("✗")]
                print(f"     🚨 겨냥한 문구가 없다: /{want}/")
                print(f"        실제 불일치: {hits or '없음'}")

    print(f"\n--- 검출 {caught} / {len(MUTANTS)} ---")
    if caught != len(MUTANTS):
        print("🚨 못 잡은 축이 있다 — 그 축은 선언만 있고 집행이 없다.")
        return 1
    print("⚠️ 이 초록이 말하지 않는 것: 등급 `C` 의 **사람 판정 자체**는 어떤 변이로도")
    print("   흔들 수 없다. 「정본에 없다」만 기계가 재기 때문이다(로그 §1).")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)
