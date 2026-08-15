#!/usr/bin/env python3
"""`ac_table_check.py` 자신을 검사한다 — 틀린 seed 를 넣으면 잡는가 (rev.14 신설).

**왜 있는가.** `ac_table_check.py` 는 §8-1 표가 적은 실측을 실행과 대조하는 도구다.
그런데 그 도구가 초록을 낸다는 것과, **틀린 표에서 빨강을 낸다는 것은 다르다.** 이
리포는 그 차이로 두 번 데었다(`mutate_rahab_captions.py` 머리말). 실제로 이 판에서도
`t-derived` 축이 **키 접두로 출처를 역산하다 다른 도구의 키를 섞어** 거짓 빨강을 냈다 —
빨강이라 눈에 띄었을 뿐이고 방향이 반대였으면 초록으로 지나갔다.

🚨 **rc 만 보지 않는다.** 열두 축이 다 같은 `1` 을 내므로 엉뚱한 축이 대신 걸려도
rc 로는 통과한다. 여기서는 **겨냥한 키가 실제로 그 목록(FAIL 또는 BLOCKED)에 있는지**
까지 본다.

🚨 **기준선이 초록이 아닌 축이 있다.** `t-thresh`(AC-12 루브릭 미달)·`t-rc`·`t-tally`
따위는 이 판의 기준선에서 이미 빨강이다. **늘 빨강인 축에 「빨강이 났다」를 대면
아무것도 증명되지 않는다.** 그런 축은 **역방향 변이**로 잰다 — 결함을 고친 사본에서
그 키가 목록에서 **사라지는지** 본다(`want="GONE"`).

⚠️ **`t-mut` 은 이 러너 안에서 끈다**(`AC_TABLE_NO_MUT`). 켜면 판정기 한 번이 돌연변이
러너 셋을 다시 돌려 실행 시간이 배로 든다. 대신 `t-mut` 겨냥 변이 하나에서만 켠다.

    python3 scripts/mutate_ac_table.py
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "docs/SEED-RAHAB.md"
CHECK = ROOT / "scripts/ac_table_check.py"

SRC = SEED.read_text(encoding="utf-8")

LINE = re.compile(r"^\s*\[(FAIL|BLOCKED)\s*\]\s*(\S+)")


def line_with(*needles: str) -> str:
    """모든 조각을 담은 **유일한** 줄. 유일하지 않으면 변이가 겨냥한 축과 실제로
    무너진 축이 어긋난다(`mutate_rahab_captions.py` 가 세운 규율)."""
    hits = [ln for ln in SRC.splitlines() if all(n in ln for n in needles)]
    if len(hits) != 1:
        # 🚨 여기는 **import 시점**이라 맨 아래 `main()` 의 except 가 못 잡는다. 그대로
        #    두면 AssertionError 가 rc `1` 로 나가는데, 이 러너의 `1` 은 「돌았고 못 잡은
        #    축이 있다」는 뜻이다. **안 돈 것이 돈 것으로 읽힌다** — 실제로 rev.14 가
        #    이 자리에서 그렇게 되었다(아래 `TOOLS` 주석). §8-1 rc 규약의 **126(실행
        #    실패)** 으로 끊어, 「러너를 못 돌렸다」와 「러너가 구멍을 찾았다」를 가른다.
        sys.stderr.write(
            f"실행 실패: 변이 대상 확정 실패 — {needles} 를 담은 줄이 "
            f"{len(hits)}개다(1개여야 한다). seed 문안이 바뀌었으면 이 needle 을 고칠 것.\n")
        raise SystemExit(126)
    return hits[0]


AC2 = line_with("| AC-2  |", "--baseline")
AC4 = line_with("| AC-4  |")
AC7 = line_with("| AC-7  |")
AC9 = line_with("| AC-9  |")
AC11 = line_with("| AC-11 |")
AC12 = line_with("| AC-12 |")
AC15 = line_with("| AC-15 |")
TALLY = line_with("**합계 — PASS", "16건 중")
PASS_BULLET = line_with("- **PASS 9**")
EXPECT = line_with("기대 rc 가 `0` 이 아닌 한 줄")
G10 = line_with("| `G10`", "955줄")
# 🚨 rev.15 정정 — 이 needle 은 seed 에 **한 번도 없던 문자열**이었다(`·` 구분).
#    `ac_table_check.py` 가 **출력할 때** 구분자를 `·` 로 정규화하는데, rev.14 가 그
#    출력을 보고 needle 을 적었다. seed 원문은 `/` 다. 그래서 이 러너는 커밋된 순간부터
#    import 에서 죽었고(§9 의 「검출 15 / 15 · rc=0」은 그 상태로 적혀 있었다), 게다가
#    죽는 rc 가 `1` 이라 「돌았는데 못 잡았다」와 구별되지 않았다. 위 `line_with` 가
#    이제 126 으로 끊는다.
# 🚨 겨냥은 **판정되는 줄**이어야 한다. `t-tools` 가 재는 것은 **오늘 재현되는**
#    합계뿐이므로 2671 행(지금 실행의 합계)이 그 축의 자리다.
#    ⚠️ 2026-08-15 갱신 — 여기 원래 「3152 행(rev.10~12 기록)은 판정기가 일부러
#    BLOCKED 로 두는 칸이라 변이시켜도 빨강이 안 난다」고 적혀 있었다. 그 칸은 이제
#    `t-hist` 가 **그 판 본문과 대조해서** 판정한다(아래 HIST 변이). 옛 판 수치도
#    더는 무판정 지대가 아니다.
TOOLS = line_with("**합계 PASS 10 / FAIL 0 / BLOCKED 18")
MUT14 = "검출 14 / 14"
# rev.10 의 기록으로 적힌 수치. 오늘 재현될 수 없으니 `t-tools` 는 못 재고,
# rev.10 커밋 본문에 실재하는지는 `t-hist` 가 잰다.
HIST = "**PASS 5 / FAIL 2 / BLOCKED 20 (rc=1)**"

# (겨냥한 축, 검사 키, 기대, 설명, before, after)
#   기대 — "FAIL": 그 키가 FAIL 목록에 뜬다 · "BLOCKED": BLOCKED 목록에 뜬다
#          "GONE": **기준선에서 이미 빨강인 축**이므로, 고친 사본에서 사라져야 한다
MUTANTS: list[tuple[str, str, str, str, str, str]] = [
    ("t-rows", "t-rows", "FAIL",
     "합계의 「16건 중」을 15 로 — 표 행 수와 어긋난다",
     TALLY, TALLY.replace("16건 중", "15건 중")),
    ("t-cmd(부재)", "t-cmd", "FAIL",
     "AC-4 의 판정기를 없는 파일로 — 실행되지 않는 명령이 실측을 낼 수는 없다",
     AC4, AC4.replace("occurrence_check.py", "occurrence_check_NOPE.py")),
    ("t-cmd(재귀)", "t-cmd", "BLOCKED",
     "AC-15 의 판정기를 이 표의 판정기 자신으로 — 무한 재귀를 BLOCKED 로 보고해야 한다",
     AC15, AC15.replace("scripts/self_count_check.py docs/SEED-RAHAB.md",
                        "scripts/ac_table_check.py docs/SEED-RAHAB.md")),
    ("t-rc", "t-rc", "FAIL",
     "AC-4 의 실측을 0 → 1 로 — 지금 실행의 rc 와 어긋난다",
     AC4, AC4.replace("| 0    | **0**", "| 0    | **1**")),
    # 🚨 부재는 FAIL 이 아니라 **BLOCKED** 다 — 지목된 행이 실행에 없으면 판정기는
    #    「어긋났다」가 아니라 「잴 수 없다」를 말해야 한다. 처음엔 FAIL 로 적었고
    #    변이가 그 기대를 무너뜨려 **기대 쪽이 틀렸다는 것**을 알려 줬다.
    ("t-derived(부재)", "t-derived", "BLOCKED",
     "AC-9 가 지목하는 파생 키를 없는 키로 — 지목된 행이 그 실행에 없다",
     AC9, AC9.replace("`G5b` 행", "`G5z` 행")),
    ("t-derived(값)", "t-derived", "FAIL",
     "AC-7 의 실측을 2 → 0 으로 — 파생 키들을 접은 rc 와 어긋난다",
     AC7, AC7.replace("| 0    | **2**", "| 0    | **0**")),
    ("t-none", "t-none", "FAIL",
     "AC-11(판정기 없음)의 실측을 2 → 0 으로 — 미착수를 초록으로 두는 형태다",
     AC11, AC11.replace("| 0    | **2**", "| 0    | **0**")),
    ("t-thresh(역)", "t-thresh", "GONE",
     "AC-12 의 실측을 문턱 위로 — 기준선에서 이미 빨강인 축이라 **사라지는지**로 잰다",
     AC12, AC12.replace("**0.37**", "**0.95**")),
    ("t-tally(수)", "t-tally", "FAIL",
     "합계의 PASS 를 9 → 8 로 — 행에서 센 것과 어긋난다",
     TALLY, TALLY.replace("PASS 9", "PASS 8")),
    ("t-tally(목록)", "t-tally", "FAIL",
     "PASS 목록의 AC-15 를 AC-14 로 — 수는 그대로고 이름만 틀린 형태다",
     PASS_BULLET, PASS_BULLET.replace("AC-15", "AC-14")),
    ("t-expect", "t-expect", "FAIL",
     "「기대 rc 가 0 이 아닌 한 줄」을 두 줄로 — rev.13 이 실제로 저지른 결함이다",
     EXPECT, EXPECT.replace("한 줄", "두 줄")),
    ("t-baseline", "t-baseline", "FAIL",
     "AC-2 의 baseline 을 rev.10 커밋으로 되돌린다 — 정정 AA 가 잡은 그 결함이다",
     AC2, re.sub(r"--baseline [0-9a-f]{7,40}", "--baseline e39368c", AC2)),
    ("t-tools", "t-tools", "FAIL",
     "본문이 인용한 러너 합계를 rev.11 의 옛 값으로 — 정정 AC 가 잡은 형태다",
     TOOLS, TOOLS.replace("PASS 10 / FAIL 0 / BLOCKED 18",
                          "PASS 7 / FAIL 0 / BLOCKED 20")),
    ("t-hist", "t-hist", "FAIL",
     "rev.10 의 기록을 5/2/20 → 5/3/20 으로 — 옛 수치 옮겨 적기 오기(정정 AC 형태)",
     HIST, HIST.replace("FAIL 2", "FAIL 3")),
    ("t-lines", "t-lines", "FAIL",
     "`G10` 행의 줄 수를 955 → 956 으로 — 정정 AB 가 잡은 형태다",
     G10, G10.replace("955줄", "956줄")),
    ("t-mut", "t-mut", "FAIL",
     "§9 의 「검출 14 / 14」를 13 으로 — 돌연변이 러너의 수치도 손 옮김이다",
     MUT14, MUT14.replace("14 / 14", "13 / 14")),
]

# `t-mut` 겨냥 변이에서만 판정기의 `t-mut` 축을 켠다. 나머지는 끄고 돈다 —
# 켜면 변이 하나마다 돌연변이 러너 셋이 더 돌아 실행 시간이 배로 든다.
MUT_AXIS = "t-mut"


def run(seed: Path, with_mut: bool) -> tuple[int, set[str], set[str]]:
    env = {**os.environ}
    # 🚨 러너가 판정기를 부르고 판정기가 다시 러너를 부르면 무한 재귀다.
    #    켠 경우에도 **자식 쪽에서는 반드시 꺼진다**(판정기의 `run()` 이 물려준다).
    if with_mut:
        env.pop("AC_TABLE_NO_MUT", None)
    else:
        env["AC_TABLE_NO_MUT"] = "1"
    p = subprocess.run(
        [sys.executable, str(CHECK), str(seed), "--git-path", "docs/SEED-RAHAB.md"],
        capture_output=True, text=True, cwd=ROOT, env=env)
    fails, blocked = set(), set()
    for ln in p.stdout.splitlines():
        if m := LINE.match(ln):
            (fails if m.group(1) == "FAIL" else blocked).add(m.group(2))
    return p.returncode, fails, blocked


def main() -> int:
    base_rc, base_fail, base_blk = run(SEED, with_mut=False)
    print(f"기준선: rc={base_rc}")
    print(f"  FAIL    {sorted(base_fail) or '없음'}")
    print(f"  BLOCKED {sorted(base_blk) or '없음'}")
    print("  ⚠️ 기준선이 초록이 아닌 축은 위 목록에 뜬 것들이다 — 지금은 `t-thresh`")
    print("     (AC-12 루브릭 미달)가 정당하게 빨강이고 `t-mut` 이 정당하게 BLOCKED 다.")
    print("     그런 축은 「빨강이 났다」가 아니라 **역방향**으로 잰다.\n")

    caught = 0
    with tempfile.TemporaryDirectory() as d:
        docs = Path(d) / "docs"
        docs.mkdir()
        for axis, key, want, why, before, after in MUTANTS:
            assert SRC.count(before) == 1, (axis, SRC.count(before))
            # 🚨 변이가 원본과 같으면 그것은 검사가 아니라 아무것도 아닌 실행이다.
            assert after != before, f"{axis}: 변이가 원본과 같다 — 무효 변이"
            ms = docs / "MUTANT-SEED.md"
            ms.write_text(SRC.replace(before, after), encoding="utf-8")
            rc, fails, blk = run(ms, with_mut=(key == MUT_AXIS))
            if want == "FAIL":
                hit = key in fails and key not in base_fail
            elif want == "BLOCKED":
                hit = key in blk and key not in base_blk
            else:                                   # GONE — 역방향
                hit = key in base_fail and key not in fails
            caught += hit
            print(f"{'✅' if hit else '❌'} {axis:14s} {key:11s} {want:7s} rc={rc}  — {why}")
            if not hit:
                print(f"     🚨 기대 {want} 가 성립하지 않았다."
                      f" FAIL={sorted(fails) or '없음'} BLOCKED={sorted(blk) or '없음'}")

    print(f"\n--- 검출 {caught} / {len(MUTANTS)} ---")
    if caught != len(MUTANTS):
        print("🚨 못 잡은 축이 있다 — 그 축은 선언만 있고 집행이 없다.")
        return 1
    print("⚠️ 이 초록이 말하지 않는 것 셋.")
    print("   ① 「단언」 열이 그 판정기가 재는 것인지 — 판정기 자신이 못 재는 자리다.")
    print("   ② **현재 판 개정 사유 절의 표 행**은 판정 대상 밖이라(정정 표가 옛 값을")
    print("      인용해야 하기 때문) 그 칸에 든 낡은 수치는 이 변이들도 못 잡는다.")
    print("   ③ 변이 목록은 판정기가 **선언한 축의 목록**이지 결함 공간의 표본이 아니다.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)
