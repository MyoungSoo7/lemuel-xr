#!/usr/bin/env python3
"""
CI 게이트 러너 — 판정 결과가 **기록된 기준선과 같은지**를 잰다.

왜 "통과"가 아니라 "기준선과 같은지"인가 (2026-08-11):
    12개 인물 게이트 중 **초록인 것은 하나도 없다**. rc 규약상 BLOCKED 가 하나만
    있어도 rc=1 이기 때문이다(`newchar_gates.py`). 그래서 이 스위트를 그대로 CI 에
    붙이면 첫날부터 빨강이고, 사흘 뒤엔 아무도 안 본다 — 그게 지금까지 12,688줄이
    CI 밖에 있던 이유다.

    그렇다고 `|| true` 로 덮으면 이 리포가 가장 싫어하는 것(아무것도 거절하지 않는
    초록)이 된다. 그래서 **지금 상태를 그대로 기록해 두고 그 상태에서 벗어나는지**를
    잰다. 빨강이 빨강인 채로 유지되는 것은 통과, 빨강이 늘어나는 것은 실패,
    빨강이 줄어드는 것도 **실패**다 — 줄었으면 기준선을 갱신해서 그 사실을
    기록으로 남겨야 하기 때문이다(`ac_table_check.py` 의 `t-baseline` 과 같은 규율).

이 초록이 말하지 않는 것:
    ① 게이트가 통과한다 — 아니다. 지금은 12개 전부 rc=1 이다.
    ② 콘텐츠가 안전하다 — BLOCKED 항목은 여전히 판정 불가 상태다.
    ③ 기준선이 옳다 — 기준선은 "오늘 이랬다"는 기록이지 목표가 아니다.

rc 규약 (리포 공통):
    0  기준선과 완전히 일치
    1  드리프트 (악화든 개선이든 — 개선은 기준선 갱신을 강제한다)
    2  판정 불가 (기준선 파일 없음 · 러너 실행 실패 · 출력 파싱 실패)
  126+ 이 스크립트 자체의 실행 실패

사용:
    python3 scripts/ci_gates.py              # 기준선과 대조 (CI 가 쓰는 형태)
    python3 scripts/ci_gates.py --update     # 지금 상태를 기준선으로 기록
    python3 scripts/ci_gates.py --only ruth  # 러너 하나만
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts" / "gates" / "BASELINE.json"
TIMEOUT = 900

TALLY_RE = re.compile(r"---\s*PASS\s+(\d+)\s*/\s*FAIL\s+(\d+)\s*/\s*BLOCKED\s+(\d+)\s*---")
AXIS_RE = re.compile(r"\[(FAIL|BLOCKED)\s*\]\s+(\S+)")


def runners() -> list[tuple[str, list[str], str]]:
    """(이름, argv, 파싱 방식) 목록. 게이트 설정이 늘면 자동으로 따라 는다."""
    out: list[tuple[str, list[str], str]] = []
    for cfg in sorted((ROOT / "scripts" / "gates").glob("*.yml")):
        name = cfg.stem
        out.append((f"gates:{name}", ["scripts/newchar_gates.py", "--character", name, "--json"], "json"))
    out.append(("ac-table:rahab", ["scripts/ac_table_check.py", "docs/SEED-RAHAB.md"], "text"))
    out.append(("track-b:readiness", ["scripts/track_b_readiness.py"], "text"))
    # 인물별 자막·정본 대조기. 위 glob 이 `scripts/gates/*.yml` 만 훑으므로 여기에
    # 명시하지 않으면 **CI 에서 한 번도 돌지 않는다.**
    #
    # 2026-08-11 실측으로 그 상태였다: `check_ruth_captions.py` 는 13개 검사(AC 23 latch
    # 3키 *값* 대조, AC 30 성구 자구 전수 포함)를 갖고 있으면서 어느 CI 경로에도
    # 걸려 있지 않았다. 워크플로가 파이썬 쪽에서 부르는 것은 `ci_gates.py` 와
    # `check_frontend_hotline.py` 둘뿐이다(.github/workflows/ci.yml:40 · :279).
    # 존재하지만 돌지 않는 검사기는 검사기가 아니라 문서다.
    out.append(("captions:ruth", ["scripts/check_ruth_captions.py", "--all"], "text"))
    out.append(("captions:rahab", ["scripts/check_rahab_captions.py"], "text"))
    return out


def run(argv: list[str]) -> tuple[int, str]:
    proc = subprocess.run(
        [sys.executable, *argv],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=TIMEOUT,
    )
    return proc.returncode, proc.stdout + proc.stderr


def observe(name: str, argv: list[str], mode: str) -> dict | None:
    """러너 하나를 돌려 (rc, 집계, 비-PASS 항목) 을 뽑는다. 못 뽑으면 None — 판정 불가다."""
    try:
        rc, out = run(argv)
    except (subprocess.TimeoutExpired, OSError) as exc:
        print(f"  [BLOCKED] {name} — 실행 실패: {exc}")
        return None

    if rc >= 126:
        print(f"  [BLOCKED] {name} — rc={rc} (실행 실패)")
        return None

    if mode == "json":
        try:
            data = json.loads(out[out.index("{") :])
        except (ValueError, json.JSONDecodeError):
            print(f"  [BLOCKED] {name} — JSON 파싱 실패")
            return None
        summary = data["summary"]
        tally = [summary["pass"], summary["fail"], summary["blocked"]]
        items = sorted(
            f"{r['gate']}:{r['status']}" for r in data["results"] if r["status"] != "PASS"
        )
    else:
        m = TALLY_RE.search(out)
        if not m:
            print(f"  [BLOCKED] {name} — 집계 줄을 찾지 못했다")
            return None
        tally = [int(m.group(1)), int(m.group(2)), int(m.group(3))]
        items = sorted(f"{axis}:{status}" for status, axis in AXIS_RE.findall(out))

    return {"rc": rc, "tally": tally, "items": items}


def fmt(tally: list[int]) -> str:
    return f"PASS {tally[0]} / FAIL {tally[1]} / BLOCKED {tally[2]}"


def compare(name: str, now: dict, was: dict | None) -> str:
    """'ok' | 'drift' | 'new' — 표준출력에 사유를 찍는다."""
    if was is None:
        print(f"  [DRIFT  ] {name} — 기준선에 없는 러너다. --update 로 기록하라.")
        return "new"

    if now == was:
        print(f"  [SAME   ] {name}  rc={now['rc']}  {fmt(now['tally'])}")
        return "ok"

    print(f"  [DRIFT  ] {name}  rc {was['rc']} → {now['rc']}")
    print(f"            기준선 {fmt(was['tally'])}")
    print(f"            지  금 {fmt(now['tally'])}")

    gone = [i for i in was["items"] if i not in now["items"]]
    added = [i for i in now["items"] if i not in was["items"]]
    for i in added:
        print(f"            + {i}  (새로 빨강/판정불가가 됐다)")
    for i in gone:
        print(f"            - {i}  (해소됐다 — 기준선을 갱신해 기록으로 남겨라)")
    return "drift"


def main() -> int:
    ap = argparse.ArgumentParser(description="게이트 결과를 기록된 기준선과 대조한다.")
    ap.add_argument("--update", action="store_true", help="지금 상태를 기준선으로 기록한다")
    ap.add_argument("--only", help="러너 이름 일부와 일치하는 것만 돌린다")
    args = ap.parse_args()

    todo = [r for r in runners() if not args.only or args.only in r[0]]
    if not todo:
        print(f"[BLOCKED] --only {args.only!r} 와 맞는 러너가 없다 — 판정할 대상이 없다.")
        return 2

    base: dict = {}
    if BASELINE.exists():
        base = json.loads(BASELINE.read_text(encoding="utf-8")).get("runners", {})
    elif not args.update:
        print(f"[BLOCKED] 기준선이 없다: {BASELINE.relative_to(ROOT)} — `--update` 로 먼저 기록하라.")
        return 2

    print(f"게이트 {len(todo)}개를 돌린다 — 기준선 대조 (판정기 통과 여부가 아니다)\n")

    observed: dict[str, dict] = {}
    blocked: list[str] = []
    verdicts: list[str] = []

    for name, argv, mode in todo:
        got = observe(name, argv, mode)
        if got is None:
            blocked.append(name)
            continue
        observed[name] = got
        if not args.update:
            verdicts.append(compare(name, got, base.get(name)))

    if args.update:
        merged = dict(base)
        merged.update(observed)
        BASELINE.write_text(
            json.dumps(
                {
                    "note": (
                        "게이트 판정의 **현재 상태 기록**이다. 초록 목표치가 아니다. "
                        "이 값과 달라지면 CI 가 멈춘다 — 나아졌을 때도 멈춘다(기록을 강제하기 위해)."
                    ),
                    "rc_convention": "0=PASS · 1=FAIL · 2=BLOCKED(판정 불가) · 126+=실행 실패",
                    "runners": merged,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print(f"\n기준선 기록: {BASELINE.relative_to(ROOT)} ({len(merged)}개 러너)")
        if blocked:
            print(f"⚠️ 실행하지 못한 러너 {len(blocked)}개는 기록에서 빠졌다: {', '.join(blocked)}")
            return 2
        return 0

    n_drift = verdicts.count("drift") + verdicts.count("new")
    n_same = verdicts.count("ok")
    print(f"\n--- 일치 {n_same} / 드리프트 {n_drift} / 판정불가 {len(blocked)} ---")
    print("  ⚠️ 이 초록은 '게이트가 통과했다'가 아니다 — 지금 12개 게이트는 전부 rc=1 이다.")
    print("     '기록해 둔 판정 결과에서 벗어나지 않았다' 까지가 이 검사의 주장 범위다.")

    if blocked:
        print(f"  ⚠️ 판정 불가 {len(blocked)}개: {', '.join(blocked)} — 통과로 읽지 마라.")
        return 2
    return 1 if n_drift else 0


if __name__ == "__main__":
    sys.exit(main())
