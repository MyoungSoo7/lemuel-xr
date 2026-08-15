#!/usr/bin/env python3
"""§8-1 **수용기준 표 자신**을 실행으로 대조한다 (rev.14 신설).

이 seed 는 열세 판 동안 다섯 종류의 주장을 기계로 재 왔다 — 자구(`quote_sweep`) ·
절 행(`verse_lines_check`) · 정본 계수(`occurrence_check`) · 코드 참조
(`code_claims_check`) · 자기 나열(`list_count_check`) · 자기 계수(`self_count_check`).
**그런데 그 여섯을 지휘하는 표, 곧 §8-1 자신은 아무도 재지 않았다.**

`code_claims_check` 의 검사축 5(실측 표 재현)는 **첫 칸이 `` `xxx.py` `` 인 행**만
다시 돌린다(`scripts/code_claims_check.py:101`). §8-1 표의 첫 칸은 `AC-N` 이라
**원리적으로 대상이 아니다.** 그래서 이 표의 「실측」 열은 여덟 판 동안 **사람이 손으로
옮겨 적었고**, rev.13 시점에 실제로 어긋나 있었다:

    §8-1 AC-1 서술 「PASS 7 · FAIL 0 · BLOCKED 20」 → 실행은 PASS 10 / FAIL 0 / BLOCKED 17
    §7-3 G10 행 「MVP-RAHAB.md 935줄」               → 실제 955줄
    AC-2 의 baseline ref `e39368c`                   → rev.13 의 직전 판이 아니다
    「기대 rc 가 0 이 아닌 두 줄」                    → 표에서 기대≠0 인 행은 AC-3 하나뿐

넷 다 **문서가 자기 실측을 갱신하지 않은 자리**이고, 넷 다 기존 판정기 여섯이 전부
초록이었다. 재는 도구를 늘리는 것으로는 안 잡힌다 — **재라고 지시하는 표를 재야** 잡힌다.

검사축 열둘:

    t-rows      표의 행 수 == 합계의 「N건 중」
    t-cmd       각 행이 적은 판정기가 실재하고 실행되는가
    t-rc        직접 행: 지금 실행의 rc == 「실측」 열
    t-derived   파생 행(「같은 실행(AC-6)의 `f-*` 행」): 지목된 행의 실행에서 **그 키가
                실재하는지** 확인하고, 키들의 상태를 rc 로 접어 실측과 대조
    t-none      「판정기 없음」 행의 실측은 반드시 `2` 다 — 미착수를 초록으로 두지 않는다
    t-thresh    기대가 rc 가 아닌 행(AC-12 의 `≥0.8`): 실측 수치가 문턱을 넘는가
    t-tally     합계의 PASS/FAIL/BLOCKED 수와 **이름 목록**이 행들에서 계산한 것과 같은가
    t-expect    「기대 rc 가 0 이 아닌 N줄」류 주장 == 표에서 센 값
    t-baseline  baseline git ref 가 **직전 판**(rev.N-1)의 커밋인가 — 마지막 손 옮김
    t-tools     본문 절이 인용한 도구 합계(PASS n · FAIL m · BLOCKED k)가 **지금 도는
                어느 실행과도 같지 않으면** 빨강. 옛 판을 이름으로 단 문단은 그 판의
                기록이므로 판정하지 않고 목록에만 찍는다
    t-mut       본문 절이 인용한 돌연변이 러너의 「검출 N / M · rc=R」 == 지금 실행
    t-lines     본문 절이 인용한 `docs/*.md: N줄` == 실제 줄 수

**개정 사유 절은 판정하지 않는다.** `## rev.N 개정 사유` 아래는 그 판 시점의 기록이라
갱신 대상이 아니다 — 과거 수치를 오늘 값으로 고치라고 요구하면 문서가 자기 역사를
지우게 된다. 판정 대상은 `## 0.` 이후의 본문 절과 **현재 판의 개정 사유 절**이되,
그 절의 **정정 표 행은 뺀다** — 정정 표의 칸은 「직전 판이 무엇을 틀리게 적고 있었나」
이고, 거기 인용된 옛 값을 오늘 값과 대조하면 **정직한 기록이 영구히 빨강**이 된다.
빼는 것은 표 행뿐이고 같은 절의 산문은 계속 판정한다. 🚨 이 예외가 사각이다(DP-R18).

못 잡는 것: 「단언」 열이 실제로 그 판정기가 재는 것인지. 명령이 초록이라는 사실과
그 명령이 그 단언을 재고 있다는 것은 다르고, 후자는 사람이 읽어야 한다.

    python3 scripts/ac_table_check.py docs/SEED-RAHAB.md
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

REV = re.compile(r"\brev\.(\d+)\b")
HEAD_REV = re.compile(r"^##\s+rev\.(\d+)\s")
HEAD_BODY = re.compile(r"^##\s+\d")
AC_ROW = re.compile(r"^\|\s*(AC-[\w-]+)\s*\|(.*)\|\s*$")
CMD = re.compile(r"`(python3\s+scripts/[\w./-]+\.py[^`]*)`")
KEY_REF = re.compile(r"`([A-Za-z][\w-]*\*?)`\s*행")
STATUS = re.compile(r"^\s*\[(PASS|FAIL|BLOCKED)\s*\]\s*(\S+)")
TALLY = re.compile(
    r"합계\s*[—-]\s*PASS\s*(\d+)\s*·\s*FAIL\s*(\d+)\s*·\s*BLOCKED\s*(\d+)\s*\((\d+)건")
BULLET = re.compile(r"^-\s*\*\*(PASS|FAIL|BLOCKED)\s*(\d+)\*\*\s*[—-]\s*(.*)$")
RUNNER = re.compile(
    r"PASS\s*(\d+)\s*[·/]\s*FAIL\s*(\d+)\s*[·/]\s*BLOCKED\s*(\d+)")
DOC_LINES = re.compile(r"`(docs/[\w./-]+\.md)`[^|`\n]{0,40}?(\d+)\s*줄")
BASELINE = re.compile(r"--baseline\s+([0-9a-f]{7,40})")
EXPECT_N = re.compile(r"기대\s*rc\s*가\s*`?0`?\s*이\s*아닌\s*([한일두세네다섯여섯]+)\s*줄")
KO_NUM = {"한": 1, "일": 1, "두": 2, "세": 3, "네": 4, "다섯": 5, "여섯": 6}
# `python3 scripts/mutate_x.py` → **검출 N / M · rc=R**
MUT = re.compile(
    r"`python3\s+(scripts/mutate_[\w]+\.py)`\s*(?:→|->)\s*\*\*검출\s*(\d+)\s*/\s*(\d+)"
    r"\s*·\s*rc=(\d+)")
MUT_OUT = re.compile(r"---\s*검출\s*(\d+)\s*/\s*(\d+)\s*---")
NO_MUT = "AC_TABLE_NO_MUT"          # 중첩 실행에서 t-mut 을 끈다 — 무한 재귀 차단
RC_TAIL = 25                        # t-rc 불일치 때 붙일 판정기 출력 꼬리 줄 수


def strip_md(s: str) -> str:
    return re.sub(r"[*`]", "", s).strip()


def git(*args: str) -> str:
    r = subprocess.run(["git", *args], capture_output=True, text=True, cwd=ROOT)
    return r.stdout.strip() if r.returncode == 0 else ""


def run(cmd: str) -> tuple[int, str]:
    """명령 문자열을 리포 루트에서 그대로 돌린다.

    자식에게는 `AC_TABLE_NO_MUT` 를 물려준다 — 돌연변이 러너가 이 도구를 다시 부를 때
    그 안에서 `t-mut` 이 또 러너를 부르면 무한 재귀다. 깊이 1 에서 끊는다.
    """
    argv = cmd.split()
    if argv[:1] == ["python3"]:
        argv = [sys.executable, *argv[1:]]
    env = {**os.environ, NO_MUT: "1"}
    try:
        r = subprocess.run(argv, capture_output=True, text=True, cwd=ROOT,
                           timeout=1800, env=env)
    except (OSError, subprocess.TimeoutExpired) as e:
        return 127, f"실행 실패: {e}"
    return r.returncode, r.stdout + r.stderr


def fold(states: list[str]) -> int:
    """키 상태들을 §8-1 rc 규약으로 접는다: BLOCKED 우선, 다음 FAIL."""
    if "BLOCKED" in states:
        return 2
    return 1 if "FAIL" in states else 0


def live_lines(lines: list[str], cur_rev: int) -> set[int]:
    """판정 대상 줄 번호.

    두 가지를 뺀다.

    1. **옛 판의 개정 사유 절 전부** — 그 판 시점의 기록이라 갱신 대상이 아니다.
    2. **현재 판 개정 사유 절의 표 행** — 정정 표는 「직전 판이 무엇을 틀리게 적고
       있었나」를 적는 자리다. 그 칸에는 **틀린 옛 값이 인용으로 들어 있어야 하고**,
       그것을 오늘 값과 대조하면 정직한 기록이 영구히 빨강이 된다. 같은 절의
       **산문은 계속 판정한다** — 빼는 것은 표 행뿐이다.

    🚨 2 는 사각이다. 누군가 갱신 안 된 수치를 정정 표 칸에 넣어 두면 이 도구는
    그것을 못 본다. §10 DP-R18 에 등재한다.
    """
    live, skip, cur_reason = set(), False, False
    for i, raw in enumerate(lines, 1):
        m = HEAD_REV.match(raw)
        if m:
            skip = int(m.group(1)) != cur_rev
            cur_reason = not skip
        elif HEAD_BODY.match(raw):
            skip, cur_reason = False, False
        if skip:
            continue
        if cur_reason and raw.lstrip().startswith("|"):
            continue
        live.add(i)
    return live


def check(target: Path, git_name: str | None = None) -> int:
    lines = target.read_text(encoding="utf-8").splitlines()
    name = git_name or target.name
    fails: list[tuple[str, str]] = []
    passes: list[tuple[str, str]] = []
    blocked: list[tuple[str, str]] = []

    def ok(key: str, msg: str) -> None:
        passes.append((key, msg))

    def bad(key: str, msg: str) -> None:
        fails.append((key, msg))

    def blk(key: str, msg: str) -> None:
        blocked.append((key, msg))

    m = REV.search(lines[0]) if lines else None
    if not m:
        print(f"✗ {name}:1 에서 rev 번호를 못 읽었다 — 이 도구는 seed 문서용이다")
        return 2
    cur_rev = int(m.group(1))
    live = live_lines(lines, cur_rev)

    # ── 표 수집 ──────────────────────────────────────────────────────────────
    rows: list[dict] = []
    for i, raw in enumerate(lines, 1):
        mm = AC_ROW.match(raw)
        if not mm or i not in live:
            continue
        cells = [c.strip() for c in mm.group(2).split("|")]
        if len(cells) < 4:
            continue
        rows.append({
            "ac": mm.group(1), "line": i, "claim": cells[0],
            "tool": cells[1], "expect": strip_md(cells[2]),
            "measured": strip_md(cells[3]),
        })
    if not rows:
        print(f"✗ {name} 에서 §8-1 AC 표를 못 찾았다 — 판정 불가")
        return 2

    # ── t-cmd · t-rc : 직접 행 ───────────────────────────────────────────────
    outputs: dict[str, tuple[int, str]] = {}
    for r in rows:
        c = CMD.search(r["tool"])
        r["cmd"] = c.group(1) if c else None
        if not r["cmd"]:
            continue
        script = ROOT / r["cmd"].split()[1]
        if not script.exists():
            bad("t-cmd", f"{r['ac']}({name}:{r['line']}) 판정기 {script.name} 가 없다")
            continue
        if script.resolve() == Path(__file__).resolve():
            # 이 표를 재는 도구가 이 표의 한 행이 되면 자기를 다시 부른다.
            # 그래서 **이 도구는 §8-1 의 행이 아니다** — §9 산출물로만 등재한다.
            blk("t-cmd", f"{r['ac']}({name}:{r['line']}) 자기 자신을 판정기로 적었다"
                         f" — 무한 재귀라 돌리지 않는다. 이 도구는 표 밖에 둘 것")
            continue
        if r["cmd"] not in outputs:
            outputs[r["cmd"]] = run(r["cmd"])
        rc, out = outputs[r["cmd"]]
        if rc >= 126:
            bad("t-cmd", f"{r['ac']} 명령이 실행되지 않았다 — {out.splitlines()[0]}")
            continue
        r["rc"] = rc
        declared = re.match(r"(\d+)", r["measured"])
        if not declared:
            bad("t-rc", f"{r['ac']}({name}:{r['line']}) 실측 열이 rc 가 아니다"
                        f" — 「{r['measured']}」 · 실제 rc {rc}")
        elif int(declared.group(1)) != rc:
            # 왜 출력을 붙이는가(2026-08-11): 이 축이 「선언 0 ≠ 지금 1」까지만 찍었더니,
            # 로컬 초록 · CI 빨강인 상태에서 **CI 로그만으로는 그 판정기가 무엇을 잡았는지
            # 알 수 없었다.** rc 는 어긋났다는 사실이고, 무엇이 어긋났는지는 출력에 있다.
            tail = [ln for ln in out.splitlines() if ln.strip()][-RC_TAIL:]
            detail = "".join(f"\n              │ {ln}" for ln in tail)
            bad("t-rc", f"{r['ac']}({name}:{r['line']}) 실측 {declared.group(1)}"
                        f" ≠ 지금 실행 {rc} — `{r['cmd']}`{detail}")
        else:
            ok("t-rc", f"{r['ac']} rc {rc}")

    if outputs:
        ok("t-cmd", f"직접 행 판정기 {len(outputs)}종 전부 실행됨")

    # ── t-derived : 「같은 실행의 `x-*` 행」 ─────────────────────────────────
    index: dict[str, tuple[str, str]] = {}   # key -> (cmd, status)
    for cmd, (_rc, out) in outputs.items():
        for ln in out.splitlines():
            s = STATUS.match(ln)
            if s:
                index.setdefault(s.group(2), (cmd, s.group(1)))

    by_ac = {r["ac"]: r for r in rows}
    for r in rows:
        if r["cmd"]:
            continue
        keys = [k for k in KEY_REF.findall(r["tool"])]
        if not keys:
            continue
        # 출처 행은 **명시돼야** 한다. `f-*` 같은 접두는 두 도구가 나눠 쓰고 있어
        # (`f-corpus` 는 staging · 나머지는 captions) 키만으로 역산하면 갈래를 넘는다.
        src = re.search(r"(AC-[\w-]+)", r["tool"])
        srccmd = by_ac.get(src.group(1), {}).get("cmd") if src else None
        if not srccmd:
            blk("t-derived", f"{r['ac']}({name}:{r['line']}) 판정기 칸이 출처 행을"
                             f" 지목하지 않는다 — 「같은 실행(AC-N)의 …」로 적을 것"
                             f" (키 접두는 도구 사이에서 유일하지 않다)")
            continue
        matched: list[str] = []
        for k in keys:
            pat = re.compile("^" + re.escape(k).replace(r"\*", ".*") + "$")
            matched += [x for x, (c, _s) in index.items() if c == srccmd and pat.match(x)]
        if not matched:
            blk("t-derived", f"{r['ac']}({name}:{r['line']}) {src.group(1)} 실행에"
                             f" 「{', '.join(keys)}」 키가 없다 — 파생 근거 미해소")
            continue
        foreign = sorted(x for x in index
                         if index[x][0] != srccmd
                         and any(re.match("^" + re.escape(k).replace(r"\*", ".*") + "$", x)
                                 for k in keys))
        if foreign:
            ok("t-derived", f"{r['ac']} ⚠️ 같은 접두의 타 도구 키 {foreign} 는"
                            f" {src.group(1)} 실행 밖이라 세지 않았다")
        states = [index[k][1] for k in sorted(set(matched))]
        matched = sorted(set(matched))
        got = fold(states)
        r["rc"] = got
        if r["measured"] in {"PASS", "FAIL", "BLOCKED"}:
            want = {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[r["measured"]]
            hit = want == got
        else:
            d = re.match(r"(\d+)", r["measured"])
            hit = bool(d) and int(d.group(1)) == got
        if hit:
            ok("t-derived", f"{r['ac']} 파생 {len(matched)}키 → {got}")
        else:
            bad("t-derived", f"{r['ac']}({name}:{r['line']}) 실측 「{r['measured']}」"
                             f" ≠ 파생 실행 {got} (키 {sorted(matched)} = {states})")

    # ── t-none · t-thresh : 판정기 없음 ─────────────────────────────────────
    for r in rows:
        if r["cmd"] or KEY_REF.search(r["tool"]):
            continue
        if "판정기 없음" not in r["tool"]:
            bad("t-none", f"{r['ac']}({name}:{r['line']}) 판정기 칸을 해석하지 못했다"
                          f" — 「{strip_md(r['tool'])[:40]}」")
            continue
        # 기대가 rc 가 아닌 행은 rc 규약 밖이다 — 문턱으로 잰다.
        th = re.match(r"[≥>]=?\s*([\d.]+)", r["expect"])
        if th:
            got = re.search(r"[\d.]+", r["measured"])
            if not got:
                blk("t-thresh", f"{r['ac']}({name}:{r['line']}) 문턱 {th.group(1)} 인데"
                                f" 실측 「{r['measured']}」에서 수치를 못 읽었다")
                r["rc"] = 2
            elif float(got.group(0)) < float(th.group(1)):
                r["rc"] = 1
                bad("t-thresh", f"{r['ac']}({name}:{r['line']}) 실측 {got.group(0)}"
                                f" < 문턱 {th.group(1)} — 미달이다")
            else:
                r["rc"] = 0
                ok("t-thresh", f"{r['ac']} {got.group(0)} ≥ {th.group(1)}")
            continue
        r["rc"] = 2
        if not r["measured"].startswith("2"):
            bad("t-none", f"{r['ac']}({name}:{r['line']}) 판정기가 없는데 실측이"
                          f" 「{r['measured']}」 — 규약상 2 다")
        else:
            ok("t-none", f"{r['ac']} 판정기 없음 · 실측 2")

    # ── t-rows · t-tally ────────────────────────────────────────────────────
    tally_line = next(((i, TALLY.search(l)) for i, l in enumerate(lines, 1)
                       if i in live and TALLY.search(l)), None)
    if not tally_line:
        blk("t-tally", "합계 줄을 못 찾았다")
    else:
        i, t = tally_line
        p, f, b, total = (int(x) for x in t.groups())
        if total != len(rows):
            bad("t-rows", f"{name}:{i} 합계는 {total}건인데 표는 {len(rows)}행")
        else:
            ok("t-rows", f"표 {len(rows)}행 == 합계 {total}건")

        calc: dict[str, list[str]] = {"PASS": [], "FAIL": [], "BLOCKED": []}
        undecided = []
        for r in rows:
            if "rc" not in r:
                undecided.append(r["ac"])
                continue
            if r["rc"] == 2:
                calc["BLOCKED"].append(r["ac"])
            elif str(r["rc"]) == r["expect"] or r["expect"] in {"PASS"} and r["rc"] == 0:
                calc["PASS"].append(r["ac"])
            else:
                calc["FAIL"].append(r["ac"])
        if undecided:
            blk("t-tally", f"판정되지 않은 행 {undecided} — 합계를 셀 수 없다")
        else:
            got = (len(calc["PASS"]), len(calc["FAIL"]), len(calc["BLOCKED"]))
            if got != (p, f, b):
                bad("t-tally", f"{name}:{i} 합계 선언 PASS {p}·FAIL {f}·BLOCKED {b}"
                               f" ≠ 실행 계산 PASS {got[0]}·FAIL {got[1]}·BLOCKED {got[2]}")
            else:
                ok("t-tally", f"합계 PASS {p}·FAIL {f}·BLOCKED {b}")
            for j, raw in enumerate(lines, 1):
                if j not in live or not (j > i and j < i + 8):
                    continue
                bm = BULLET.match(raw.strip())
                if not bm:
                    continue
                kind, n, names = bm.group(1), int(bm.group(2)), bm.group(3)
                listed = set(re.findall(r"AC-[\w-]+", names))
                want = set(calc[kind])
                if listed != want or n != len(want):
                    bad("t-tally", f"{name}:{j} {kind} 목록 {sorted(listed)}(선언 {n})"
                                   f" ≠ 실행 계산 {sorted(want)}")
                else:
                    ok("t-tally", f"{kind} 목록 {len(want)}건 일치")

    # ── t-expect ────────────────────────────────────────────────────────────
    for i, raw in enumerate(lines, 1):
        if i not in live:
            continue
        em = EXPECT_N.search(raw)
        if not em:
            continue
        want = KO_NUM.get(em.group(1))
        # rc 를 말하는 주장이므로 **기대가 rc 인 행만** 센다(문턱·PASS 표기는 rc 가 아니다).
        actual = [r["ac"] for r in rows
                  if re.fullmatch(r"\d+", r["expect"]) and r["expect"] != "0"]
        if want != len(actual):
            bad("t-expect", f"{name}:{i} 「기대 rc 가 0 이 아닌 {em.group(1)} 줄」"
                            f" — 표에서 센 것은 {len(actual)}줄 {actual}")
        else:
            ok("t-expect", f"기대≠0 {len(actual)}줄 {actual}")

    # ── t-baseline ──────────────────────────────────────────────────────────
    # `--all` 이다. HEAD 계보만 걸으면 **squash 병합이 이 축을 조용히 죽인다** —
    # 2026-08-12 PR #33 을 squash 로 병합하자 rev.14 를 담은 커밋이 main 의 파일
    # 이력에서 사라져 이 축이 PASS 에서 BLOCKED 로 떨어졌다(판정 불가는 통과가
    # 아니다). 그 커밋은 태그 `rahab-rev14-baseline` 으로 도달성을 유지시켰고,
    # 여기서는 어느 ref 에서든 직전 판을 찾도록 바꾼다. 판정의 뜻은 그대로다 —
    # 「선언된 baseline SHA 가 실제 직전 판의 커밋인가」.
    # ⚠️ 얕은 클론이면 여전히 조용히 판정 불가다. ci.yml 의 fetch-depth: 0 이
    #    이 축의 전제이고, 그 줄을 지우면 여기가 먼저 죽는다.
    prev = ""
    for sha in git("log", "--all", "--format=%H", "--", f"docs/{name}").splitlines():
        head = git("show", f"{sha}:docs/{name}").splitlines()
        hm = REV.search(head[0]) if head else None
        if hm and int(hm.group(1)) == cur_rev - 1:
            prev = sha
            break
    for r in rows:
        bm = BASELINE.search(r["tool"])
        if not bm:
            continue
        declared = git("rev-parse", bm.group(1))
        if not prev:
            blk("t-baseline", f"{r['ac']} 직전 판(rev.{cur_rev - 1})의 커밋을 git 에서"
                              f" 못 찾았다 — baseline `{bm.group(1)}` 을 검증할 수 없다")
        elif declared != prev:
            bad("t-baseline", f"{r['ac']}({name}:{r['line']}) baseline"
                              f" `{bm.group(1)}` 은 직전 판이 아니다"
                              f" — rev.{cur_rev - 1} 은 {prev[:7]}")
        else:
            ok("t-baseline", f"{r['ac']} baseline {prev[:7]} == rev.{cur_rev - 1}")

    # ── t-tools : 본문 절이 인용한 도구 합계 ────────────────────────────────
    # 지금 도는 실행들의 합계 집합. 인용은 **이 중 하나와 같아야** 한다 —
    # 어느 것과도 같지 않으면 그 수치는 오늘 아무 데서도 나오지 않는다.
    tallies: dict[tuple[int, int, int], list[str]] = {}
    for cmd, (_rc, out) in outputs.items():
        gm = re.search(r"PASS\s*(\d+)\s*/\s*FAIL\s*(\d+)\s*/\s*BLOCKED\s*(\d+)", out)
        if gm:
            tallies.setdefault(tuple(int(x) for x in gm.groups()), []).append(
                cmd.split()[1].split("/")[-1])
    if not tallies:
        blk("t-tools", "합계를 내는 판정기가 이 표에 없다 — 잴 것이 없다")
    else:
        tally_lineno = tally_line[0] if tally_line else -1
        # 옛 판 표식은 **바로 앞 줄까지만** 본다 — 「한 줄 위에 rev.10 이 …」가 실제
        # 형태다(3341→3342).
        # 🚨 rev.15 정정 — 여기는 원래 **문단 전체**를 봤다. 그러면 뒤에 붙은 판 기록이
        #    앞의 **현재 합계**를 면제시킨다: 2671 의 「합계 PASS 10 / FAIL 0 / BLOCKED 18」
        #    은 같은 문단 2679 의 「rev.10 최초 실행은 …」 때문에 면제 대상이었고, 그
        #    값이 낡는 순간 FAIL 이 아니라 BLOCKED(「사람이 볼 것」)로 조용히 빠졌다.
        #    이 seed 에서 가장 많이 인용되는 수치가 정확히 무방비였다. `mutate_ac_table.py`
        #    의 `t-tools` 변이가 그것을 드러냈다(검출 14/15).
        # ── t-hist 준비 : rev.N 을 선언한 커밋 ──────────────────────────────
        # 옛 판의 수치는 **오늘 재현될 수 없다** — 그 판의 스크립트와 콘텐츠가
        # 그때 낸 출력이기 때문이다. 그래서 여기는 오래 「판정하지 않는다(사람이
        # 볼 것)」로 비워 두었는데, 그러면 이 seed 가 가장 자주 저지르는 결함이
        # 정확히 그 빈칸에 숨는다: **옛 수치를 옮겨 적다 틀리는 것**(정정 AC —
        # 「§7-3 합계 줄은 고쳤는데 그 합계를 해설하는 문단 다섯 곳은 옛 값을
        # 들고 있었다」, 정정 E, 정정 AE 가 전부 같은 형태다).
        #
        # 재현은 못 해도 **대조는 된다.** 「rev.10 의 기록」이라고 적은 수치는
        # rev.10 을 선언한 커밋의 본문에 실제로 있어야 한다. 없으면 그것은 역사가
        # 아니라 오기이고, 그건 기계가 판정할 수 있다.
        #
        # ⚠️ t-baseline 과 같은 이유로 `--all` 이고 얕은 클론이면 판정 불가다.
        #    못 찾으면 BLOCKED 로 남긴다 — 못 찾은 것을 통과로 바꾸지 않는다.
        rev_sha: dict[int, str] = {}
        rev_walked = [False]

        def hist_verify(lineno: int, trio: tuple[int, ...], n: int) -> None:
            if not rev_walked[0]:
                rev_walked[0] = True
                # log 는 최신순 — setdefault 라서 그 판을 선언한 **마지막** 커밋,
                # 곧 rev.N 의 최종 상태가 남는다(초판이 아니라).
                for sha in git("log", "--all", "--format=%H",
                               "--", f"docs/{name}").splitlines():
                    head = git("show", f"{sha}:docs/{name}").splitlines()
                    hm = REV.search(head[0]) if head else None
                    if hm:
                        rev_sha.setdefault(int(hm.group(1)), sha)
            label = (f"{name}:{lineno} PASS {trio[0]}·FAIL {trio[1]}"
                     f"·BLOCKED {trio[2]} — rev.{n} 의 기록")
            if n not in rev_sha:
                blk("t-hist", f"{label}인데 rev.{n} 을 선언한 커밋을 git 에서"
                              f" 못 찾았다 — 대조할 원본이 없다")
                return
            body = git("show", f"{rev_sha[n]}:docs/{name}")
            found = {tuple(int(x) for x in m.groups())
                     for m in RUNNER.finditer(body)}
            if trio in found:
                ok("t-hist", f"{label} — rev.{n} 본문({rev_sha[n][:7]})에 실재한다")
            else:
                bad("t-hist", f"{label}으로 적혀 있는데 rev.{n} 본문"
                              f"({rev_sha[n][:7]})에 그 수치가 없다"
                              f" — 그 판에 있던 것은 {sorted(found)}")

        seen = 0
        for i, raw in enumerate(lines, 1):
            if i not in live or i == tally_lineno:
                continue
            for rm in RUNNER.finditer(raw):
                trio = tuple(int(x) for x in rm.groups())
                # **일치가 먼저다.** 오늘 재현되는 수치는 판 표식이 무엇이든 참이다.
                # 재현되지 않을 때만 「옛 판의 기록인가」를 묻는다 — 순서를 뒤집으면
                # 판 이름이 붙었다는 이유로 낡은 수치가 영원히 면제된다.
                if trio not in tallies:
                    # 표 행은 **한 줄에 판 역사가 통째로** 들어 있어(§9) 같은 줄의 다른
                    # rev 언급이 면제 근거로 새어 든다. 그래서 표 행에서는 수치 **바로
                    # 앞에 붙은** 판 표식만 인정한다.
                    old = [int(x) for x in
                           re.findall(r"rev\.(\d+)[\s·]*$", raw[:rm.start()][-14:])]
                    if not raw.lstrip().startswith("|"):
                        ctx = " ".join(lines[max(0, i - 2):i])
                        old += [int(x) for x in re.findall(
                            r"(?<!도구 )rev\.(\d+)\s*(?:이|가|까지|최초|시점|에서)", ctx)]
                    if any(n < cur_rev for n in old):
                        # 오늘 재현되지 않는 옛 판의 수치 — t-tools 로는 잴 수 없다.
                        # 버리지 않고 t-hist 로 넘겨 **그 판 본문과 대조**한다.
                        hist_verify(i, trio, min(old))
                        continue
                seen += 1
                if trio in tallies:
                    ok("t-tools", f"{name}:{i} {tallies[trio]} 합계와 일치")
                else:
                    bad("t-tools", f"{name}:{i} PASS {trio[0]}·FAIL {trio[1]}·BLOCKED"
                                   f" {trio[2]} 는 지금 도는 어느 실행과도 다르다"
                                   f" — 실제 {[(k, v) for k, v in tallies.items()]}")
        if not seen:
            blk("t-tools", "본문 절에 판정 대상 합계 인용이 없다")

    # ── t-mut : 본문이 인용한 돌연변이 러너의 「검출 N / M」 ─────────────────
    # 판정기의 초록은 「틀린 입력을 잡는가」를 말하지 않는다. 그것을 말하는 것은
    # 러너의 검출 수치이고, 그 수치도 여덟 판 동안 손으로 옮겨 적혔다(정정 AE).
    if os.environ.get(NO_MUT):
        pass                                  # 중첩 실행 — 재귀 차단. 축 자체를 빼둔다
    else:
        seen_mut = 0
        for i, raw in enumerate(lines, 1):
            if i not in live:
                continue
            for path, cn, cm, crc in MUT.findall(raw):
                seen_mut += 1
                mp = ROOT / path
                if not mp.exists():
                    bad("t-mut", f"{name}:{i} 인용한 {path} 가 없다")
                    continue
                # 🚨 **이 도구를 겨냥한 러너는 이 도구가 돌리지 않는다.** 돌리면
                #    「자기를 재는 표가 자기를 잰다」의 다른 얼굴이다 — 재귀는
                #    깊이 1 에서 끊기지만, 그렇게 끊긴 실행은 **얕은 자기 검사**라
                #    초록이 무엇을 뜻하는지 말할 수 없다. 사람이 따로 돌린다.
                if Path(__file__).name in mp.read_text(encoding="utf-8"):
                    blk("t-mut", f"{name}:{i} {path} 는 이 도구 자신의 돌연변이"
                                 f" 러너다 — 자기가 자기를 돌리지 않는다(DP-R18)."
                                 f" 이 칸과의 대조는 러너 자신이 한다(CI mutation-gate)")
                    continue
                rc, out = run(f"python3 {path}")
                om = MUT_OUT.search(out)
                if not om:
                    blk("t-mut", f"{name}:{i} {path} 출력에서 「검출 N / M」을 못 읽었다"
                                 f" — rc={rc}")
                    continue
                got = (om.group(1), om.group(2), str(rc))
                if got != (cn, cm, crc):
                    bad("t-mut", f"{name}:{i} {path} 「검출 {cn}/{cm} · rc={crc}」"
                                 f" ≠ 실행 검출 {got[0]}/{got[1]} · rc={got[2]}")
                else:
                    ok("t-mut", f"{path} 검출 {cn}/{cm} · rc={crc}")
        if not seen_mut:
            blk("t-mut", "본문 절에 돌연변이 러너의 검출 수치 인용이 없다 — 잴 것이 없다")

    # ── t-lines : `docs/*.md: N줄` ──────────────────────────────────────────
    seen_lines = 0
    for i, raw in enumerate(lines, 1):
        if i not in live:
            continue
        for path, n in DOC_LINES.findall(raw):
            p = ROOT / path
            if not p.exists():
                bad("t-lines", f"{name}:{i} 인용한 {path} 가 없다")
                continue
            actual = len(p.read_text(encoding="utf-8").splitlines())
            seen_lines += 1
            if int(n) != actual:
                bad("t-lines", f"{name}:{i} 「{path}: {n}줄」 ≠ 실제 {actual}줄")
            else:
                ok("t-lines", f"{path} {actual}줄")
    if not seen_lines:
        blk("t-lines", "본문 절에 줄 수 인용이 없다 — 잴 것이 없다")

    # ── 출력 ────────────────────────────────────────────────────────────────
    for key, msg in fails:
        print(f"  [FAIL   ] {key:<11} {msg}")
    for key, msg in blocked:
        print(f"  [BLOCKED] {key:<11} {msg}")
    for key, msg in passes:
        print(f"  [PASS   ] {key:<11} {msg}")
    print(f"--- PASS {len(passes)} / FAIL {len(fails)} / BLOCKED {len(blocked)} ---")
    print("  ⚠️ 이 초록이 말하는 것: 표가 적은 실측이 **지금 실행과 같다**. "
          "그 판정기가 그 단언을 재고 있는지는 사람이 읽어야 한다.")
    print("  rc: 0=PASS · 1=FAIL · 2=BLOCKED (SEED-RAHAB.md §8-1 규약)")
    return 1 if fails else (2 if blocked else 0)


if __name__ == "__main__":
    argv = sys.argv[1:]
    git_name = None
    if "--git-path" in argv:                 # 변이 검사용 — 변이본을 원본의 이력으로 본다
        i = argv.index("--git-path")
        git_name = Path(argv[i + 1]).name
        del argv[i:i + 2]
    if len(argv) != 1:
        sys.exit(__doc__)
    arg = Path(argv[0])
    sys.exit(check(arg if arg.is_absolute() else ROOT / arg, git_name))
